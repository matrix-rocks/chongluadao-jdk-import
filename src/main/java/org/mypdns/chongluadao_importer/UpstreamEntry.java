package org.mypdns.chongluadao_importer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpstreamEntry {
    @JsonProperty("_id")
    public String id;

    @JsonProperty("url")
    public String url;

    @JsonProperty("meta")
    public Object meta;

}
