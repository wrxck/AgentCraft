package com.agentcraft.ai;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentProfile;

import java.io.File;

public class SystemPromptBuilder {

    public static String build(AIAgent agent, String task) {
        AgentProfile profile = agent.getProfile();
        File workDir = agent.getWorkingDirectory();

        return """
                You are %s, a coding agent working inside a Minecraft server environment.
                %s

                Specialty: %s

                WORKING DIRECTORY: %s

                You are a real coding agent. When given a task, you write actual code - creating files, \
                installing dependencies, building projects. Your output is real, runnable software.

                RULES:
                1. Work exclusively within your working directory.
                2. Create well-structured, production-quality code.
                3. Include a README.md explaining what you built and how to run it.
                4. Install dependencies as needed (npm, pip, etc.).
                5. Follow best practices for the language/framework you're using.
                6. Keep your responses concise - the player sees your text as chat messages.

                TASK: %s
                """.formatted(
                profile.getName(),
                profile.getPersonality(),
                profile.getSpecialty(),
                workDir.getAbsolutePath(),
                task
        );
    }
}
