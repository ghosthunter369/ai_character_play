package com.character.voice;

import com.character.config.RAGConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleKnowledgeTest {
    @Test void roleDocumentsAreLoadedFromClasspathWithoutFilesystemOrNetwork() throws Exception {
        var documents=new RAGConfig().documents();
        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().anyMatch(doc -> "苏格拉底".equals(doc.metadata().getString("appName"))));
        assertTrue(documents.stream().allMatch(doc -> !doc.text().isBlank()));
    }
}
