package com.sap.cloud.lm.sl.cf.core.validators.parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.cloudfoundry.multiapps.common.util.Tester;
import org.cloudfoundry.multiapps.common.util.Tester.Expectation;
import org.cloudfoundry.multiapps.mta.model.Module;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HostValidatorTest {

    private final Tester tester = Tester.forClass(getClass());

    private final HostValidator validator = new HostValidator();

    private final boolean isValid;
    private final String host;
    private final Expectation expectation;

    public HostValidatorTest(String host, boolean isValid, Expectation expectation) {
        this.isValid = isValid;
        this.host = host;
        this.expectation = expectation;
    }

    @Parameters
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
// @formatter:off
            // (0)
            { "TEST_TEST_TEST", false, new Expectation("test-test-test"), },
            // (1)
            { "test-test-test", true , new Expectation("test-test-test"), },
            // (2)
            { "---", false, new Expectation(Expectation.Type.EXCEPTION, "Could not create a valid host from \"---\"") },
            // (3)
            { "@12", false, new Expectation("12"), },
            // (4)
            { "@@@", false, new Expectation(Expectation.Type.EXCEPTION, "Could not create a valid host from \"@@@\"") },
// @formatter:on
        });
    }

    @Test
    public void testValidate() {
        assertEquals(isValid, validator.isValid(host, null));
    }

    @Test
    public void testCanCorrect() {
        assertTrue(validator.canCorrect());
    }

    @Test
    public void testAttemptToCorrect() {
        tester.test(() -> validator.attemptToCorrect(host, null), expectation);
    }

    @Test
    public void testGetParameterName() {
        assertEquals("host", validator.getParameterName());
    }

    @Test
    public void testGetContainerType() {
        assertTrue(validator.getContainerType()
                            .isAssignableFrom(Module.class));
    }

}
