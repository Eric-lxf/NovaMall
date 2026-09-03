"""Offline golden-contract probe, not a production validator or an AI quality test.

The catalog/slots are trusted server inputs, never accepted from model output.
This small fixture catalog has one source version. Production supports multiple.
"""
import json


class ContractError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ContractError(message)


def fields(value, expected, label):
    require(isinstance(value, dict), f"{label}: expected object")
    require(set(value) == set(expected), f"{label}: missing or unknown fields")


def string(value, maximum, label):
    require(isinstance(value, str) and 0 < len(value.strip()) <= maximum,
            f"{label}: expected nonempty bounded string")
    require(len(value) <= maximum, f"{label}: raw length exceeds limit")


def array(value, minimum, maximum, label):
    require(isinstance(value, list) and minimum <= len(value) <= maximum,
            f"{label}: invalid array length/type")


def identifiers(value, minimum, maximum, allowed, label):
    array(value, minimum, maximum, label)
    for item in value:
        string(item, 64, label)
        require(item in allowed, f"{label}: outside authorized scope")
    require(len(set(value)) == len(value), f"{label}: duplicate ID")


def parse_payload(raw):
    """Strict JSON only: no fences, duplicate properties, NaN, or oversized input."""
    require(isinstance(raw, str), "payload: expected text")
    try:
        encoded_length = len(raw.encode("utf-8"))
    except UnicodeEncodeError as exc:
        raise ContractError("payload: invalid Unicode") from exc
    require(encoded_length <= 128 * 1024, "payload: exceeds 128 KiB")

    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "payload: duplicate property")
            result[key] = value
        return result

    def invalid_constant(value):
        raise ContractError(f"payload: invalid constant {value}")

    try:
        return json.loads(raw, object_pairs_hook=object_pairs, parse_constant=invalid_constant)
    except (json.JSONDecodeError, RecursionError) as exc:
        raise ContractError("payload: malformed JSON") from exc


def validate_batch(batch, catalog, slots):
    """Raise ContractError on untrusted response mismatch; no semantic correctness claim."""
    fields(batch, {"schemaVersion", "questions"}, "batch")
    require(batch["schemaVersion"] == "exam.question.v1", "batch: unsupported version")
    questions = batch["questions"]
    array(questions, 1, 5, "questions")
    require(len(questions) == len(slots), "questions: incomplete requested batch")
    fragments = {f["id"]: f["text"] for f in catalog["fragments"]}
    common = {"slotId", "type", "stem", "analysis", "knowledgePointIds", "sourceRefs"}
    extras = {
        "SINGLE_CHOICE": {"options", "correctOptionIds"},
        "MULTIPLE_CHOICE": {"options", "correctOptionIds"},
        "TRUE_FALSE": {"answerBoolean"},
        "SHORT_ANSWER": {"referenceAnswer", "rubric"},
    }
    seen_slots, seen_stems = set(), set()
    for q in questions:
        require(isinstance(q, dict), "question: expected object")
        kind = q.get("type")
        require(isinstance(kind, str) and kind in extras, "question: unsupported type")
        fields(q, common | extras[kind], "question")
        slot_id = q["slotId"]
        string(slot_id, 64, "slotId")
        require(slot_id in slots and slot_id not in seen_slots, "slotId: unknown or duplicate")
        seen_slots.add(slot_id)
        slot = slots[slot_id]
        require(kind == slot["type"], "type: does not match blueprint")
        string(q["stem"], 2000, "stem")
        normalized = " ".join(q["stem"].split())
        require(normalized not in seen_stems, "stem: duplicate within batch")
        seen_stems.add(normalized)
        string(q["analysis"], 4000, "analysis")
        identifiers(q["knowledgePointIds"], 1, 8, slot["knowledgePointIds"], "knowledgePointIds")
        refs = q["sourceRefs"]
        array(refs, 1, 8, "sourceRefs")
        seen_refs = set()
        for ref in refs:
            fields(ref, {"sourceVersionId", "fragmentId", "quote"}, "sourceRef")
            string(ref["sourceVersionId"], 64, "sourceVersionId")
            require(ref["sourceVersionId"] == catalog["sourceVersionId"], "sourceVersionId: outside scope")
            fragment_id = ref["fragmentId"]
            string(fragment_id, 64, "fragmentId")
            require(fragment_id in fragments and fragment_id in slot["fragmentIds"], "fragmentId: outside scope")
            string(ref["quote"], 1000, "quote")
            require(ref["quote"] in fragments[fragment_id], "quote: not a verbatim source excerpt")
            pair = (fragment_id, ref["quote"])
            require(pair not in seen_refs, "sourceRef: duplicate")
            seen_refs.add(pair)
        if kind in {"SINGLE_CHOICE", "MULTIPLE_CHOICE"}:
            options = q["options"]
            array(options, 2, 6, "options")
            option_ids, option_texts = set(), set()
            for index, option in enumerate(options):
                fields(option, {"id", "text"}, "option")
                require(option["id"] == chr(ord("A") + index), "option: IDs must be sequential A-F")
                string(option["text"], 1000, "option.text")
                text = " ".join(option["text"].split())
                require(text not in option_texts, "option: duplicate text")
                option_ids.add(option["id"])
                option_texts.add(text)
            minimum = 1 if kind == "SINGLE_CHOICE" else 2
            maximum = 1 if kind == "SINGLE_CHOICE" else len(options)
            identifiers(q["correctOptionIds"], minimum, maximum, option_ids, "correctOptionIds")
        elif kind == "TRUE_FALSE":
            require(type(q["answerBoolean"]) is bool, "answerBoolean: must be JSON boolean")
        else:
            string(q["referenceAnswer"], 4000, "referenceAnswer")
            array(q["rubric"], 1, 10, "rubric")
            total, points = 0, set()
            for item in q["rubric"]:
                fields(item, {"point", "weight"}, "rubric item")
                string(item["point"], 1000, "rubric.point")
                point = " ".join(item["point"].split())
                require(point not in points, "rubric: duplicate point")
                points.add(point)
                require(type(item["weight"]) is int and 1 <= item["weight"] <= 100,
                        "rubric.weight: expected integer 1-100")
                total += item["weight"]
            require(total == 100, "rubric: weights must total 100")
    return batch


def student_projection(validated_batch):
    """Allowlist fields for layout tests only; production also requires finalized snapshot/auth."""
    questions = []
    for q in validated_batch["questions"]:
        projected = {key: q[key] for key in ("slotId", "type", "stem")}
        if "options" in q:
            projected["options"] = [{"id": o["id"], "text": o["text"]} for o in q["options"]]
        questions.append(projected)
    return {"questions": questions}
