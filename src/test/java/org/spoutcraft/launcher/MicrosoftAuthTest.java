package org.spoutcraft.launcher;

import org.junit.Test;
import org.spoutcraft.launcher.modpacks.ModPackYML;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrosoftAuthTest {

    @Test
    public void cachedTokenIsNotExpiredWhenExpiryIsInTheFuture() {
        long futureExpiry = System.currentTimeMillis() + 60_000L;
        assertFalse(MicrosoftAuth.isTokenExpired(futureExpiry));
    }

    @Test
    public void cachedTokenIsExpiredWhenExpiryIsInThePast() {
        long pastExpiry = System.currentTimeMillis() - 60_000L;
        assertTrue(MicrosoftAuth.isTokenExpired(pastExpiry));
    }

    @Test
    public void cachedTokenIsExpiredWhenExpiryIsMissingOrNonPositive() {
        assertTrue(MicrosoftAuth.isTokenExpired(0L));
        assertTrue(MicrosoftAuth.isTokenExpired(-1L));
    }

    @Test
    public void msalTokenIsExpiredWhenExpiryDateIsInThePast() {
        assertTrue(MicrosoftAuth.isTokenExpired(new Date(System.currentTimeMillis() - 60_000L)));
    }

    @Test
    public void msalTokenIsNotExpiredWhenExpiryDateIsInTheFuture() {
        assertFalse(MicrosoftAuth.isTokenExpired(new Date(System.currentTimeMillis() + 60_000L)));
    }

    @Test
    public void blankBuildSelectionIsRejectedAsInvalid() {
        assertFalse(ModPackYML.isValidBuild(null));
        assertFalse(ModPackYML.isValidBuild(""));
        assertFalse(ModPackYML.isValidBuild("   "));
        assertFalse(ModPackYML.isValidBuild("-1"));
        assertTrue(ModPackYML.isValidBuild("3.1.2"));
    }

    @Test
    public void blankMinecraftVersionIsRejectedAsInvalid() {
        assertFalse(MinecraftYML.isValidVersion(null));
        assertFalse(MinecraftYML.isValidVersion(""));
        assertFalse(MinecraftYML.isValidVersion("   "));
        assertFalse(MinecraftYML.isValidVersion("-1"));
        assertTrue(MinecraftYML.isValidVersion("1.2.5"));
    }

    @Test
    public void cachedFileWithoutChecksumMetadataIsAccepted() throws IOException {
        File temp = File.createTempFile("md5-cache-test", ".zip");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write("cached archive");
        }

        assertTrue(MD5Utils.checksumPath(temp, "mods\\missing\\archive.zip"));
        temp.delete();
    }
}
