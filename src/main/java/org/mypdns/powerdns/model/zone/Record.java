package org.mypdns.powerdns.model.zone;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Record {

    @JsonProperty("content")
    public String content;

    @JsonProperty("disabled")
    public boolean disabled;

    public Record(String content, boolean disabled) {
        this.content = content;
        this.disabled = disabled;
    }
}
