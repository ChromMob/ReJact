package me.chrommob.rejact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AccountsTest {
    @TempDir Path dir;
    @Test void credentialsSurviveRestartAndInvalidInputIsRejected() {
        Accounts accounts = new Accounts(new Store(dir));
        assertFalse(accounts.register("user", null));
        assertFalse(accounts.register("user", "short"));
        assertTrue(accounts.register("user", "correct horse"));
        assertFalse(accounts.register("user", "different horse"));
        Accounts reopened = new Accounts(new Store(dir));
        assertTrue(reopened.verify("user", "correct horse"));
        assertFalse(reopened.verify("user", "wrong horse"));
        assertFalse(reopened.verify("user", null));
        assertFalse(reopened.verify("absent", "correct horse"));
    }
}
