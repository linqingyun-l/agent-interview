package lin_agent_interview.agentInterview.common.ai.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
@Slf4j
public class SkillLoader implements ApplicationRunner {
    /** Spring 4.x 推荐构造器注入（且字段必须 final）以保证依赖不漏 */
    private final SkillRegistry registry;

    public SkillLoader(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path skillsDir = resolveSkillsDir();
        if (skillsDir == null) {
            log.warn("找不到 skills 目录(试用:1) classpath:skills/ 2) src/main/resources/skills/),跳过 Skill 加载");
            return;
        }

        try (Stream<Path> stream = Files.walk(skillsDir)) {
            stream
                    .filter(p -> p.endsWith("SKILL.md"))
                    .forEach(this::loadOne);
        } catch (Exception e) {
            log.error("扫描 skills 目录失败: {}", skillsDir, e);
        }
        log.info("加载 Skill 完毕，共 {} 个", registry.count());
    }

    /**
     * 解析 skills 目录路径,多种方式轮询:
     *   1) classpath:skills/  —— 适合 IDE / mvn spring-boot:run / 解压的 jar
     *   2) 相对路径 src/main/resources/skills —— 适合源代码根目录启动
     * 都找不到返回 null(不抛异常,让应用正常启动)。
     */
    private Path resolveSkillsDir() {
        // 1. classpath:skills/
        try {
            URL url = getClass().getClassLoader().getResource("skills");
            if (url != null && "file".equals(url.getProtocol())) {
                Path p = Paths.get(url.toURI());
                if (Files.isDirectory(p)) {
                    log.debug("从 classpath 找到 skills 目录: {}", p);
                    return p;
                }
            }
        } catch (Exception ignored) {
            // classpath 解析失败就退化到下一种方式
        }
        // 2. 源码根目录下的相对路径
        Path fallback = Path.of("src", "main", "resources", "skills");
        if (Files.isDirectory(fallback)) {
            log.debug("从相对路径找到 skills 目录: {}", fallback.toAbsolutePath());
            return fallback;
        }
        return null;
    }

    /**
     * 加载单个 SKILL.md：解析 frontmatter (YAML) + Markdown 正文 → 构造 Skill → 注册。
     *
     * 行为：
     *   - 任何异常都被捕获并继续加载下一个（保证不阻塞应用启动）
     *   - id 缺失时按 父目录名 → name 依次兜底
     *   - name 缺失时按 id 兜底
     *   - frontmatter 缺失或解析失败 → 跳过并 warn
     *
     * package-private 便于测试直接调用，无需反射。
     */
    void loadOne(Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            ParsedDoc doc = parseFrontmatter(raw);
            if (doc == null) {
                log.warn("跳过 {}:缺少有效的 frontmatter (--- ... ---)", path);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fm = new Yaml().load(doc.frontmatter);
            if (fm == null || fm.isEmpty()) {
                log.warn("跳过 {}:frontmatter 为空", path);
                return;
            }

            Skill skill = new Skill();
            skill.setId(asString(fm.get("id")));
            skill.setName(asString(fm.get("name")));
            skill.setCategory(asString(fm.get("category")));
            skill.setLevel(asString(fm.get("level")));
            skill.setDescription(asString(fm.get("description")));
            skill.setTags(asStringList(fm.get("tags")));
            skill.setContent(doc.body);

            // 兜底:id 缺时 ① 用父目录名 ② 用 name
            if (skill.getId() == null || skill.getId().isBlank()) {
                Path parent = path.getParent();
                if (parent != null && parent.getFileName() != null) {
                    skill.setId(parent.getFileName().toString());
                } else if (skill.getName() != null) {
                    skill.setId(skill.getName());
                } else {
                    log.warn("跳过 {}:既无 id,也无 path 父目录可供兜底", path);
                    return;
                }
            }
            // name 缺时用 id
            if (skill.getName() == null || skill.getName().isBlank()) {
                skill.setName(skill.getId());
            }

            registry.register(skill);
            log.info("加载 Skill ✓: id={}, name={}", skill.getId(), skill.getName());
        } catch (Exception e) {
            log.error("加载 Skill 失败: {}", path, e);
        }
    }

    // ===================== Helpers =====================

    /** frontmatter 段 + Markdown 正文 */
    private record ParsedDoc(String frontmatter, String body) {}

    /**
     * 解析 frontmatter 块，约定：
     *   - 内容必须以 --- 开头（可前导空白）
     *   - 用下一个 \n--- 作为结束标记
     *   - 之后到文件末尾是 Markdown 正文
     * 失败时返回 null（如缺 frontmatter）。
     */
    private static ParsedDoc parseFrontmatter(String raw) {
        String trimmed = raw.stripLeading();
        if (!trimmed.startsWith("---")) return null;

        int fmContentStart = 3; // 越过开头的 ---
        // 跳过紧跟在 --- 后的换行
        if (fmContentStart < trimmed.length() && trimmed.charAt(fmContentStart) == '\n') {
            fmContentStart++;
        } else if (fmContentStart + 1 < trimmed.length()
                && trimmed.charAt(fmContentStart) == '\r'
                && trimmed.charAt(fmContentStart + 1) == '\n') {
            fmContentStart += 2;
        }

        // 找下一个 "\n---"
        int sepIdx = trimmed.indexOf("\n---", fmContentStart);
        if (sepIdx < 0) return null;

        String fm = trimmed.substring(fmContentStart, sepIdx);

        // 越过结尾的 \n---
        int bodyStart = sepIdx + 4;
        if (bodyStart < trimmed.length() && trimmed.charAt(bodyStart) == '\r') {
            bodyStart++;
        }
        if (bodyStart < trimmed.length() && trimmed.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return new ParsedDoc(fm, trimmed.substring(bodyStart));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
