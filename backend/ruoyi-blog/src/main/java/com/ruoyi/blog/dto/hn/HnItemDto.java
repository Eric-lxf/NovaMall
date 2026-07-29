package com.ruoyi.blog.dto.hn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HnItemDto
{

    private Long id;
    private String type;
    private String title;
    private String url;
    private String text;
    private Integer score;
    @JsonProperty("by")
    private String author;
    private Long time;
    private Integer descendants;
}
