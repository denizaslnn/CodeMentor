package com.codementor.aiservice.config;

import com.codementor.aiservice.service.CodeAnalysisEngine;
import com.codementor.aiservice.service.MockCodeAnalysisEngine;
import com.codementor.aiservice.service.OpenAiCompatibleCodeAnalysisEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * ai.engine property'sinin dogru motoru sectigini dogrular.
 * Tam Spring context'i (DB/Redis/Rabbit) ayaga kaldirmadan, yalnizca ilgili
 * config siniflari ile calisir.
 * <p>
 * Context'e bilincli olarak HICBIR RestClient.Builder bean'i konmaz: ai-service'in
 * gercek classpath'inde de yoktur. Boylece bu test, uretimdeki wiring'i birebir
 * yansitir (once builder bean'i stub'lanmisti ve eksikligi ancak container ayaga
 * kalkmayinca fark edilmisti).
 */
class EngineSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiEngineConfig.class, MockCodeAnalysisEngine.class);

    @Test
    void defaultsToInProcessMockWhenPropertyMissing() {
        runner.run(context -> assertInstanceOf(MockCodeAnalysisEngine.class,
                context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void selectsMockEngineExplicitly() {
        runner.withPropertyValues("ai.engine=mock")
                .run(context -> assertInstanceOf(MockCodeAnalysisEngine.class,
                        context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void selectsOpenAiEngineWhenConfigured() {
        runner.withPropertyValues(
                        "ai.engine=openai",
                        "ai.openai.base-url=http://mock-vllm:8000",
                        "ai.openai.model=mock-code-analyzer")
                .run(context -> assertInstanceOf(OpenAiCompatibleCodeAnalysisEngine.class,
                        context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void onlyOneEngineBeanIsRegistered() {
        runner.withPropertyValues(
                        "ai.engine=openai",
                        "ai.openai.base-url=http://mock-vllm:8000",
                        "ai.openai.model=mock-code-analyzer")
                .run(context -> assertEquals(1, context.getBeansOfType(CodeAnalysisEngine.class).size()));
    }
}
