package com.zillya.timonfech.zillwrapper.core.communication.sections;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ControlMessageComposer {

    private final List<ControlMessageSectionRenderer> renderers;

    public String compose(ControlMessageContext context) {
        return renderers.stream()
                .filter(renderer -> renderer.supports(context))
                .sorted(Comparator.comparingInt(ControlMessageSectionRenderer::order))
                .map(renderer -> renderer.render(context))
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}

