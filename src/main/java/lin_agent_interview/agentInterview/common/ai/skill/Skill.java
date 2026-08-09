package lin_agent_interview.agentInterview.common.ai.skill;

import lombok.Data;

import java.util.List;
@Data

public class Skill {
    private String id;
    private String name;
    private String category;
    private String level;
    private String description;
    private List<String> tags;
    private String content;
}
