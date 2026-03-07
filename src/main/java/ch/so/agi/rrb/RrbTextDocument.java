package ch.so.agi.rrb;

import java.util.List;

record RrbTextDocument(
        int year,
        int rrbNumber,
        String sourcePageUrl,
        String publicPdfUrl,
        String filename,
        int pageCount,
        List<RrbTextChunk> chunks) {

    RrbTextDocument {
        chunks = List.copyOf(chunks);
    }

    @Override
    public List<RrbTextChunk> chunks() {
        return chunks;
    }
}
