import copy
import json
from pathlib import Path
import unittest

from contract import ContractError, parse_payload, student_projection, validate_batch

SAMPLES = Path(__file__).resolve().parent / "samples"


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.batch = json.loads((SAMPLES / "question-batch.json").read_text(encoding="utf-8"))
        self.catalog = json.loads((SAMPLES / "fragments.json").read_text(encoding="utf-8"))
        self.slots = json.loads((SAMPLES / "slots.json").read_text(encoding="utf-8"))

    def check(self):
        return validate_batch(self.batch, self.catalog, self.slots)

    def rejected(self):
        with self.assertRaises(ContractError):
            self.check()

    def test_four_types_valid(self):
        original = copy.deepcopy(self.batch)
        self.check()
        self.assertEqual(original, self.batch)

    def test_strict_json_round_trip(self):
        self.assertEqual(self.batch, parse_payload(json.dumps(self.batch)))

    def test_no_json_fences(self):
        with self.assertRaises(ContractError):
            parse_payload('```json\n{}\n```')

    def test_no_duplicate_json_fields(self):
        with self.assertRaises(ContractError):
            parse_payload('{"type":"SINGLE_CHOICE","type":"TRUE_FALSE"}')

    def test_no_nan_or_oversized_payload(self):
        for raw in ('{"weight":NaN}', '"' + 'a' * (128 * 1024) + '"', '\ud800'):
            with self.subTest(raw_length=len(raw)), self.assertRaises(ContractError):
                parse_payload(raw)

    def test_unknown_version(self):
        self.batch["schemaVersion"] = "exam.question.v99"
        self.rejected()

    def test_model_cannot_assign_review_or_owner(self):
        for key in ("reviewStatus", "ownerUserId", "questionId", "score"):
            with self.subTest(key=key):
                self.batch["questions"][0][key] = "APPROVED"
                self.rejected()
                del self.batch["questions"][0][key]

    def test_missing_batch_question(self):
        self.batch["questions"].pop()
        self.rejected()

    def test_duplicate_slot(self):
        self.batch["questions"][1]["slotId"] = "s1"
        self.rejected()

    def test_unexpected_question_type(self):
        self.batch["questions"][0]["type"] = "MULTIPLE_CHOICE"
        self.rejected()

    def test_single_choice_one_answer_only(self):
        self.batch["questions"][0]["correctOptionIds"] = ["A", "B"]
        self.rejected()

    def test_multiple_choice_at_least_two_answers(self):
        self.batch["questions"][1]["correctOptionIds"] = ["A"]
        self.rejected()

    def test_option_answer_must_exist(self):
        self.batch["questions"][0]["correctOptionIds"] = ["Z"]
        self.rejected()

    def test_option_ids_and_texts_not_duplicated(self):
        option = self.batch["questions"][0]["options"][1]
        option["id"] = "A"
        self.rejected()
        option["id"] = "B"
        option["text"] = self.batch["questions"][0]["options"][0]["text"]
        self.rejected()

    def test_truth_requires_boolean_not_number_or_string(self):
        for value in (1, "true", None):
            with self.subTest(value=value):
                self.batch["questions"][2]["answerBoolean"] = value
                self.rejected()

    def test_rubric_weights_total_100(self):
        self.batch["questions"][3]["rubric"][0]["weight"] = 49
        self.rejected()

    def test_rubric_rejects_boolean_weight(self):
        self.batch["questions"][3]["rubric"][0]["weight"] = True
        self.rejected()

    def test_unknown_and_out_of_slot_source(self):
        for fragment in ("invented-fragment", "f6"):
            with self.subTest(fragment=fragment):
                self.batch["questions"][0]["sourceRefs"][0]["fragmentId"] = fragment
                self.rejected()

    def test_source_version_is_pinned(self):
        self.batch["questions"][0]["sourceRefs"][0]["sourceVersionId"] = "fixture-v2"
        self.rejected()

    def test_evidence_must_match_original_text(self):
        self.batch["questions"][0]["sourceRefs"][0]["quote"] = "新员工应在入职后五个工作日内完成设备登记。"
        self.rejected()

    def test_knowledge_point_must_be_in_slot(self):
        self.batch["questions"][0]["knowledgePointIds"] = ["privacy"]
        self.rejected()

    def test_bounded_text(self):
        self.batch["questions"][0]["stem"] = "字" * 2001
        self.rejected()

    def test_nested_malformed_inputs_fail_as_contract_error(self):
        mutations = (("sourceRefs", [None]), ("options", [1, 2]), ("type", []),
                     ("slotId", {}), ("knowledgePointIds", [{}]), ("analysis", None))
        for key, value in mutations:
            original = self.batch["questions"][0][key]
            with self.subTest(key=key):
                self.batch["questions"][0][key] = value
                self.rejected()
            self.batch["questions"][0][key] = original

    def test_student_projection_only_contains_public_question_fields(self):
        projected = student_projection(self.check())
        self.assertEqual(4, len(projected["questions"]))
        for q in projected["questions"]:
            self.assertLessEqual(set(q), {"slotId", "type", "stem", "options"})
            for option in q.get("options", []):
                self.assertEqual({"id", "text"}, set(option))
        self.assertNotIn("fixture-v1", json.dumps(projected))


if __name__ == "__main__":
    unittest.main(verbosity=2)
