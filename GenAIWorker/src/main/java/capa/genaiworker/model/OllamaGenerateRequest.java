package capa.genaiworker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OllamaGenerateRequest {

    private String model;
    private String prompt;
    private boolean stream;
    private Options options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Options {
        @JsonProperty("num_ctx")
        private int numCtx;

        @JsonProperty("num_predict")
        private int numPredict;

        private double temperature;

        @JsonProperty("repeat_penalty")
        private double repeatPenalty;
    }
}
