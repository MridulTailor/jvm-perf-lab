package lab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.*;

class GcLogParserTest {

    @Test
    @DisplayName("parse_gc.py accurately computes pauses, percentiles, overhead, and throughput from G1 fixture")
    void testG1SampleLogParsing() throws Exception {
        File script = new File("scripts/parse_gc.py");
        File logFixture = new File("src/test/resources/fixtures/gc_g1_sample.log");
        File outFixture = new File("src/test/resources/fixtures/gc_g1_sample.out");

        assertTrue(script.exists(), "Parser script must exist");
        assertTrue(logFixture.exists(), "Log fixture must exist");

        ProcessBuilder pb = new ProcessBuilder(
                "python3", script.getAbsolutePath(),
                "--file", logFixture.getAbsolutePath(),
                "--out", outFixture.getAbsolutePath()
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
        assertTrue(json.contains("\"pause_count\": 5"), "Must identify 5 pause events in G1 fixture");
        assertTrue(json.contains("\"total_pause_ms\": 37.0"), "Total pause time must equal 37.0ms");
        assertTrue(json.contains("\"max_pause_ms\": 12.5"), "Max pause must be 12.5ms");
        assertTrue(json.contains("\"throughput_ops_sec\": 50000.0"), "Throughput must be parsed from out fixture");
        assertTrue(json.contains("\"peak_heap_mb\": 250.0"), "Peak heap before GC must be 250.0 MB");
    }

    @Test
    @DisplayName("parse_gc.py accurately parses sub-millisecond ZGC pause phases")
    void testZgcSampleLogParsing() throws Exception {
        File script = new File("scripts/parse_gc.py");
        File logFixture = new File("src/test/resources/fixtures/gc_zgc_sample.log");

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
        assertEquals(0, exitCode);

        String json = output.toString();
        assertTrue(json.contains("\"pause_count\": 6"), "Must identify 6 sub-millisecond pauses across 2 ZGC cycles");
        assertTrue(json.contains("\"max_pause_ms\": 0.082"), "Max pause must be 0.082ms (82 microseconds)");
    }
}
