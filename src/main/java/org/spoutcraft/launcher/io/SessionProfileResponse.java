package org.spoutcraft.launcher.io;

import java.util.List;

public class SessionProfileResponse {
    private String id;
    private String name;
    private List<SessionProperty> properties;

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public List<SessionProperty> getProperties() {
        return this.properties;
    }

    public static class SessionProperty {
        private String name;
        private String value;

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
        }
    }
}
