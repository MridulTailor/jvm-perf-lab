package lab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.*;

class JitLogParserTest {

    @Test
    @DisplayName("parse_jit.py accurately counts compilation tiers and inlining decisions from fixture")
    void testJitLogParsing() throws Exception {
        File script = new File("scripts/parse_jit.py");
        File logFixture = new File("src/test/resources/fixtures/jit_sample.log");

        assertTrue(script.exists(), "Parser script must exist");
        assertTrue(logFixture.exists(), "Log fixture must exist");

        ProcessBuilder pb = new ProcessBuilder(
                "python3", script.getAbsolutePath(),
                "--file", logFixture.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "Parser script must exit successfully: " + output);

        String json = output.toString();
        // Compilations check
        assertTrue(json.contains("\"total\": 8"), "Must detect 8 total compilations");
        assertTrue(json.contains("\"c2_tier4\": 6"), "Must detect 6 Tier 4 (C2) compilations");
        assertTrue(json.contains("\"osr_count\": 4"), "Must detect 4 OSR compilations");

        // Inlining decisions check
        assertTrue(json.contains("\"too_big\": 1"), "Must detect 1 too_big refusal (computeLargePipeline)");
        assertTrue(json.contains("\"virtual_or_megamorphic\": 1"), "Must detect 1 virtual call refusal");
        assertTrue(json.contains("\"lab.workload.InliningCase::computeLargePipeline\""), "Must capture decision for computeLargePipeline");
        assertTrue(json.contains("\"hot method too big\""), "Must capture rejection reason for oversized method");
    }
}
