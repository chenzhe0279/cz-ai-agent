package com.cz.czaiagent.tools;

import com.cz.czaiagent.service.HumanInteractionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AskHumanTool {

    private final HumanInteractionService humanInteractionService;

    public AskHumanTool(HumanInteractionService humanInteractionService) {
        this.humanInteractionService = humanInteractionService;
    }

    @Tool(description = """
            Use this tool to ask human for help when you lack critical information,
            face ambiguous requirements, or need user preferences or confirmation to proceed.
            Do not guess blindly when important details are missing.
            """)
    public String askHuman(
            @ToolParam(description = "The question you want to ask human.")
            String inquire
    ) {
        return humanInteractionService.askHuman(inquire);
    }
}