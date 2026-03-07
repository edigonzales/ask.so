package ch.so.agi.rrb;

record RrbDocument(
        int year,
        int rrbNumber,
        String sourcePageUrl,
        String publicPdfUrl,
        String pdfResourceUri,
        String filename,
        String pdfMimeType,
        byte[] pdfBytes) {

    RrbDocument {
        pdfBytes = pdfBytes.clone();
    }

    @Override
    public byte[] pdfBytes() {
        return pdfBytes.clone();
    }

    long byteLength() {
        return pdfBytes.length;
    }
}
