package org.springframework.samples.petclinic.qa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PQT-3: every external GitHub Action must be referenced by an immutable full-length commit SHA, followed by a
 * comment with the human readable release (for example {@code actions/checkout@<sha> # v7.0.1}).
 */
class GitHubActionsQaTest {
    private static final Pattern USES = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses:\\s*([^\\s#]+)(.*)$");
    private static final Pattern SHA40 = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern VERSION_COMMENT = Pattern.compile("^\\s*#\\s*v\\d+(\\.\\d+){0,2}\\s*$");

    private static List<Path> workflowFiles() throws IOException {
        Path workflows = Path.of(".github", "workflows");
        assertTrue(Files.isDirectory(workflows), "Pasta .github/workflows ausente");
        try (Stream<Path> caminhos = Files.walk(workflows)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .toList();
        }
    }

    @Test
    @DisplayName("TA-07 | Acoes externas fixadas por SHA de 40 caracteres com comentario de versao")
    void ta07_todasAcoesExternasDevemUsarCommitSha() throws IOException {
        List<String> violacoes = new ArrayList<>();
        int referencias = 0;
        for (Path arquivo : workflowFiles()) {
            String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
            Matcher matcher = USES.matcher(conteudo);
            while (matcher.find()) {
                String referencia = matcher.group(1);
                if (referencia.startsWith("./") || referencia.startsWith("docker://")) {
                    continue;
                }
                referencias++;
                int at = referencia.lastIndexOf('@');
                String versao = at >= 0 ? referencia.substring(at + 1) : "";
                if (!SHA40.matcher(versao).matches()) {
                    violacoes.add(arquivo + ": " + referencia + " (sem SHA completo)");
                } else if (!VERSION_COMMENT.matcher(matcher.group(2)).matches()) {
                    violacoes.add(arquivo + ": " + referencia + " (sem comentario '# vX.Y.Z')");
                }
            }
        }
        assertFalse(referencias == 0, "Nenhuma action encontrada nos workflows");
        assertTrue(violacoes.isEmpty(), "Acoes sem SHA fixo: " + violacoes);
    }
}
