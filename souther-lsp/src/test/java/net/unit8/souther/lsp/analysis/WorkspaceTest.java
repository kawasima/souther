package net.unit8.souther.lsp.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTest {

    @Test
    void snapshotScansRootForSoutherSources() throws Exception {
        Path dir = Files.createTempDirectory("ws");
        Path a = dir.resolve("a.sou");
        Files.writeString(a, "module a exposing ( N )\ndata N = { v: Int }\n");
        Files.writeString(dir.resolve("readme.md"), "not souther");

        Workspace ws = new Workspace();
        ws.setRoots(List.of(dir.toUri().toString()));
        ModuleGraph graph = ws.snapshot(Map.of());

        assertEquals(1, graph.uris().size(), "only the .sou file is picked up");
        assertTrue(graph.text(a.toUri().toString()).contains("data N"));
    }

    @Test
    void snapshotFindsSourcesInNestedDirectories() throws Exception {
        Path dir = Files.createTempDirectory("ws");
        Path nested = Files.createDirectories(dir.resolve("src/main/souther"));
        Path a = nested.resolve("a.sou");
        Files.writeString(a, "module a\ndata N = { v: Int }\n");

        Workspace ws = new Workspace();
        ws.setRoots(List.of(dir.toUri().toString()));

        assertEquals(1, ws.snapshot(Map.of()).uris().size());
    }

    @Test
    void anOpenBufferOverlaysTheOnDiskText() throws Exception {
        Path dir = Files.createTempDirectory("ws");
        Path a = dir.resolve("a.sou");
        Files.writeString(a, "module a\ndata N = { v: Int }\n");
        String uri = a.toUri().toString();

        Workspace ws = new Workspace();
        ws.setRoots(List.of(dir.toUri().toString()));
        ModuleGraph graph = ws.snapshot(Map.of(uri, "module a\ndata N = { v: String }\n"));

        assertTrue(graph.text(uri).contains("String"), "the unsaved buffer wins over disk");
    }
}
