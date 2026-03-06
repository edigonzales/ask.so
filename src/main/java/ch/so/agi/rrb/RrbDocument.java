package ch.so.agi.rrb;

record RrbDocument(
        int year,
        int rrbNumber,
        String sourcePageUrl,
        String publicPdfUrl,
        String resourceUri,
        String filename,
        String mimeType,
        byte[] pdfBytes) {

    long byteLength() {
        return pdfBytes.length;
    }
}
