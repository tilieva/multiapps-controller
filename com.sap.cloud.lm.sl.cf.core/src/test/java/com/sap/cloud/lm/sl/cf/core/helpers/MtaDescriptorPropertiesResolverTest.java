package com.sap.cloud.lm.sl.cf.core.helpers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.cloudfoundry.multiapps.common.util.Tester;
import org.cloudfoundry.multiapps.common.util.Tester.Expectation;
import org.cloudfoundry.multiapps.mta.model.DeploymentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sap.cloud.lm.sl.cf.core.cf.HandlerFactory;
import com.sap.cloud.lm.sl.cf.core.model.CloudTarget;
import com.sap.cloud.lm.sl.cf.core.model.ImmutableMtaDescriptorPropertiesResolverContext;
import com.sap.cloud.lm.sl.cf.core.model.MtaDescriptorPropertiesResolverContext;
import com.sap.cloud.lm.sl.cf.core.persistence.service.ConfigurationEntryService;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.core.util.DescriptorTestUtil;

public class MtaDescriptorPropertiesResolverTest {

    public static Stream<Arguments> testResolve() {
        return Stream.of(
// @formatter:off
            Arguments.of(
                "mtad-properties-resolver-test/mtad-with-route.yaml", new Expectation(Expectation.Type.JSON, "mtad-properties-resolver-test/mtad-with-route-result.json")
            ),
            Arguments.of(
                "mtad-properties-resolver-test/mtad-with-domain.yaml", new Expectation(Expectation.Type.JSON, "mtad-properties-resolver-test/mtad-with-domain-result.json")
            ),
            Arguments.of(
                "mtad-properties-resolver-test/mtad-with-escaped-references.yaml", new Expectation(Expectation.Type.JSON, "mtad-properties-resolver-test/mtad-with-escaped-references.json")
            )
        );
// @formatter:on
    }

    private final Tester tester = Tester.forClass(getClass());

    private MtaDescriptorPropertiesResolver resolver;

    @Mock
    private BiFunction<String, String, String> spaceIdSupplier;
    @Mock
    private ApplicationConfiguration configuration;
    @Mock
    private ConfigurationEntryService configurationEntryService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(spaceIdSupplier.apply(anyString(), anyString())).thenReturn("");

        resolver = new MtaDescriptorPropertiesResolver(buildMtaDescriptorPropertiesResolverContext());
    }

    private MtaDescriptorPropertiesResolverContext buildMtaDescriptorPropertiesResolverContext() {
        return ImmutableMtaDescriptorPropertiesResolverContext.builder()
                                                              .handlerFactory(new HandlerFactory(2))
                                                              .configurationEntryService(configurationEntryService)
                                                              .cloudTarget(new CloudTarget("", ""))
                                                              .currentSpaceId("")
                                                              .applicationConfiguration(configuration)
                                                              .namespace(null)
                                                              .applyNamespace(false)
                                                              .shouldReserveTemporaryRoute(false)
                                                              .build();
    }

    @ParameterizedTest(name = "{index}: \"{1}.\"")
    @MethodSource
    public void testResolve(String descriptorFile, Expectation expectation) {
        DeploymentDescriptor descriptor = DescriptorTestUtil.loadDeploymentDescriptor(descriptorFile, getClass());

        tester.test(() -> resolver.resolve(descriptor), expectation);
    }

}
