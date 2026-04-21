package br.com.clube_quinze.api.dto.feedback;

import java.io.Serializable;

public record FeedbackAverageResponse(String target, Double average) implements Serializable {
}
