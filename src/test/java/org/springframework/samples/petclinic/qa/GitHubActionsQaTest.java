package org.springframework.samples.petclinic.qa;

import org.junit.jupiter.api.Disabled;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubActionsQaTest {
    private static final Pattern USES = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses:\\s*([^\\s#]+)");
    private static final Pattern SHA40 = Pattern.compile("[a-fA-F0-9]{40}");

    @Test
    @Disabled("PQT-3 em aberto: habilitar apos fixar as acoes externas por SHA de 40 caracteres")
    void ta07_todasAcoesExternasDevemUsarCommitSha() throws IOException {
        Path workflows = Path.of(".github", "workflows");
        assertTrue(Files.isDirectory(workflows), "Pasta .github/workflows ausente");
        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> caminhos = Files.walk(workflows)) {
            for (Path arquivo : caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .toList()) {
                String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
                Matcher matcher = USES.matcher(conteudo);
                while (matcher.find()) {
                    String referencia = matcher.group(1);
                    if (referencia.startsWith("./") || referencia.startsWith("docker://")) {
                        continue;
                    }
                    int at = referencia.lastIndexOf('@');
                    String versao = at >= 0 ? referencia.substring(at + 1) : "";
                    if (!SHA40.matcher(versao).matches()) {
                        violacoes.add(arquivo + ": " + referencia);
                    }
                }
            }
        }
        assertTrue(violacoes.isEmpty(), "Acoes sem SHA fixo: " + violacoes);
    }
}
