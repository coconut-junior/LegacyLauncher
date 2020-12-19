package org.spoutcraft.launcher.io;

public class SkinInfo {
    private String timestamp;
    private String profileId;
    private String profileName;
    private Textures textures;

    public String getTimestamp() {
        return this.timestamp;
    }

    public String getProfileId() {
        return this.profileId;
    }

    public String getProfileName() {
        return this.profileName;
    }

    public Textures getTextures() {
        return this.textures;
    }

    public static class Textures {
        private Skin SKIN;

        public Skin getSKIN() {
            return this.SKIN;
        }

        public static class Skin {
            private String url;

            public String getUrl() {
                return this.url;
            }
        }
    }
}
