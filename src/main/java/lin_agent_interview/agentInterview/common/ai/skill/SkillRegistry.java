package lin_agent_interview.agentInterview.common.ai.skill;

import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillRegistry  {
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public void register(Skill skill) {
        skills.put(skill.getId(), skill);
    }

    public Skill get(String id) {
        Skill s = skills.get(id);
        if (s == null) {
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "面试方向不存在: " + id);
        }
        return s;
    }

    public List<Skill> list() {
        return new ArrayList<>(skills.values());
    }

    public int count() {
        return skills.size();
    }
}
