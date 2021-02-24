package org.mypdns.powerdns.model.zone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Rrset {
    @JsonProperty("name")
    public String name;

    @JsonProperty("type")
    public Type type;

    @JsonProperty("ttl")
    public long ttl;

    @JsonProperty("changetype")
    public Changetype changetype;

    @JsonProperty("records")
    public Record[] records;

    public Rrset(String name, Type type, long ttl, Changetype changetype, Record[] records) {
        this.name = name;
        this.type = type;
        this.ttl = changetype == Changetype.DELETE ? 0 : ttl;
        this.changetype = changetype;
        this.records = changetype == Changetype.DELETE ? null : records;
    }
}
