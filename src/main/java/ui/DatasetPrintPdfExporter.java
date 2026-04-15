package ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

final class DatasetPrintPdfExporter {
    private static final int CARDS_PER_PAGE = 4;

    private static final int PAGE_WIDTH_PX = 2480;
    private static final int PAGE_HEIGHT_PX = 3508;
    private static final float PAGE_WIDTH_PT = 595.28f;
    private static final float PAGE_HEIGHT_PT = 841.89f;

    private static final int OUTER_MARGIN_PX = 140;
    private static final int CARD_GAP_PX = 90;
    private static final int CARD_CORNER_RADIUS_PX = 42;
    private static final float JPEG_QUALITY = 0.9f;

    private static final Color PAGE_BACKGROUND = new Color(250, 248, 244);
    private static final Color CARD_BACKGROUND = new Color(255, 255, 255);
    private static final Color CARD_BORDER = new Color(220, 224, 230);
    private static final Color CARD_SHADOW = new Color(0, 0, 0, 22);
    private static final Color PLACEHOLDER_COLOR = new Color(120, 129, 145);
    private static final Color TITLE_COLOR = new Color(42, 52, 71);
    private static final Color TITLE_SECONDARY = new Color(118, 126, 142);

    private DatasetPrintPdfExporter() {
    }

    static void export(Path targetPdf, List<PrintableCard> cards, PrintMode printMode) throws IOException {
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("数据集为空，无法导出打印 PDF。");
        }

        Path absoluteTarget = targetPdf.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        writePdf(absoluteTarget, cards, printMode == null ? PrintMode.STANDARD : printMode);
    }

    private static void writePdf(Path targetPdf, List<PrintableCard> cards, PrintMode printMode) throws IOException {
        int pageCount = (cards.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE;
        int objectCount = 2 + pageCount * 3;
        List<Long> offsets = new ArrayList<>(Collections.nCopies(objectCount + 1, 0L));

        try (CountingOutputStream outputStream = new CountingOutputStream(
            new BufferedOutputStream(Files.newOutputStream(targetPdf)))
        ) {
            writeAscii(outputStream, "%PDF-1.4\n");
            outputStream.write(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});

            writeObject(outputStream, offsets, 1, buildCatalogObjectBody());
            writeObject(outputStream, offsets, 2, buildPagesObjectBody(pageCount));

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                int startIndex = pageIndex * CARDS_PER_PAGE;
                int endIndex = Math.min(cards.size(), startIndex + CARDS_PER_PAGE);
                BufferedImage pageImage = renderPage(cards.subList(startIndex, endIndex), printMode);
                byte[] jpegBytes = encodeJpeg(pageImage);

                int pageObjectNumber = 3 + pageIndex * 3;
                int contentObjectNumber = pageObjectNumber + 1;
                int imageObjectNumber = pageObjectNumber + 2;

                writeObject(outputStream, offsets, pageObjectNumber, buildPageObjectBody(contentObjectNumber, imageObjectNumber));
                writeObject(outputStream, offsets, contentObjectNumber, buildContentObjectBody());
                writeObject(outputStream, offsets, imageObjectNumber, buildImageObjectBody(jpegBytes));
            }

            long xrefOffset = outputStream.getCount();
            writeAscii(outputStream, "xref\n0 " + (objectCount + 1) + "\n");
            writeAscii(outputStream, "0000000000 65535 f \n");
            for (int objectNumber = 1; objectNumber <= objectCount; objectNumber++) {
                writeAscii(outputStream, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(objectNumber)));
            }

            writeAscii(outputStream, "trailer\n");
            writeAscii(outputStream, "<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\n");
            writeAscii(outputStream, "startxref\n");
            writeAscii(outputStream, Long.toString(xrefOffset));
            writeAscii(outputStream, "\n%%EOF");
        }
    }

    private static byte[] buildCatalogObjectBody() {
        return ascii("<< /Type /Catalog /Pages 2 0 R >>");
    }

    private static byte[] buildPagesObjectBody(int pageCount) {
        StringBuilder body = new StringBuilder("<< /Type /Pages /Kids [");
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int pageObjectNumber = 3 + pageIndex * 3;
            body.append(pageObjectNumber).append(" 0 R ");
        }
        body.append("] /Count ").append(pageCount).append(" >>");
        return ascii(body.toString());
    }

    private static byte[] buildPageObjectBody(int contentObjectNumber, int imageObjectNumber) {
        String body = String.format(
            Locale.ROOT,
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] " +
                "/Resources << /XObject << /Im %d 0 R >> >> /Contents %d 0 R >>",
            PAGE_WIDTH_PT,
            PAGE_HEIGHT_PT,
            imageObjectNumber,
            contentObjectNumber
        );
        return ascii(body);
    }

    private static byte[] buildContentObjectBody() throws IOException {
        String content = String.format(
            Locale.ROOT,
            "q\n%.2f 0 0 %.2f 0 0 cm\n/Im Do\nQ\n",
            PAGE_WIDTH_PT,
            PAGE_HEIGHT_PT
        );
        byte[] streamBytes = ascii(content);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writeAscii(buffer, "<< /Length " + streamBytes.length + " >>\n");
        writeAscii(buffer, "stream\n");
        buffer.write(streamBytes);
        writeAscii(buffer, "endstream");
        return buffer.toByteArray();
    }

    private static byte[] buildImageObjectBody(byte[] jpegBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writeAscii(buffer,
            "<< /Type /XObject /Subtype /Image " +
                "/Width " + PAGE_WIDTH_PX +
                " /Height " + PAGE_HEIGHT_PX +
                " /ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                "/Filter /DCTDecode /Length " + jpegBytes.length + " >>\n");
        writeAscii(buffer, "stream\n");
        buffer.write(jpegBytes);
        writeAscii(buffer, "\nendstream");
        return buffer.toByteArray();
    }

    private static void writeObject(CountingOutputStream outputStream, List<Long> offsets, int objectNumber, byte[] body)
        throws IOException {
        offsets.set(objectNumber, outputStream.getCount());
        writeAscii(outputStream, objectNumber + " 0 obj\n");
        outputStream.write(body);
        writeAscii(outputStream, "\nendobj\n");
    }

    private static BufferedImage renderPage(List<PrintableCard> cards, PrintMode printMode) {
        BufferedImage pageImage = new BufferedImage(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = pageImage.createGraphics();
        try {
            configureGraphics(graphics);
            graphics.setColor(PAGE_BACKGROUND);
            graphics.fillRect(0, 0, PAGE_WIDTH_PX, PAGE_HEIGHT_PX);

            CardLayout layout = calculateCardLayout();
            for (int index = 0; index < cards.size(); index++) {
                int column = index % 2;
                int row = index / 2;
                int x = layout.startX + column * (layout.cardWidth + CARD_GAP_PX);
                int y = layout.startY + row * (layout.cardHeight + CARD_GAP_PX);
                drawCard(graphics, cards.get(index), x, y, layout.cardWidth, layout.cardHeight, printMode);
            }
        } finally {
            graphics.dispose();
        }
        return pageImage;
    }

    private static CardLayout calculateCardLayout() {
        int cardWidth = (PAGE_WIDTH_PX - 2 * OUTER_MARGIN_PX - CARD_GAP_PX) / 2;
        int cardHeight = cardWidth * 4 / 3;

        int availableHeight = PAGE_HEIGHT_PX - 2 * OUTER_MARGIN_PX - CARD_GAP_PX;
        if (cardHeight * 2 > availableHeight) {
            cardHeight = availableHeight / 2;
            cardWidth = cardHeight * 3 / 4;
        }

        int startX = (PAGE_WIDTH_PX - (cardWidth * 2 + CARD_GAP_PX)) / 2;
        int startY = (PAGE_HEIGHT_PX - (cardHeight * 2 + CARD_GAP_PX)) / 2;
        return new CardLayout(startX, startY, cardWidth, cardHeight);
    }

    private static void drawCard(
        Graphics2D graphics,
        PrintableCard card,
        int x,
        int y,
        int width,
        int height,
        PrintMode printMode
    ) {
        graphics.setColor(CARD_SHADOW);
        graphics.fillRoundRect(x + 14, y + 18, width, height, CARD_CORNER_RADIUS_PX, CARD_CORNER_RADIUS_PX);

        graphics.setColor(CARD_BACKGROUND);
        graphics.fillRoundRect(x, y, width, height, CARD_CORNER_RADIUS_PX, CARD_CORNER_RADIUS_PX);
        graphics.setColor(CARD_BORDER);
        graphics.setStroke(new BasicStroke(4f));
        graphics.drawRoundRect(x, y, width, height, CARD_CORNER_RADIUS_PX, CARD_CORNER_RADIUS_PX);

        if (printMode == PrintMode.ALBUM) {
            drawAlbumCard(graphics, card, x, y, width, height);
            return;
        }

        Shape previousClip = graphics.getClip();
        graphics.setClip(new RoundRectangle2D.Float(x, y, width, height, CARD_CORNER_RADIUS_PX, CARD_CORNER_RADIUS_PX));
        drawCardImage(graphics, card.imagePath(), x, y, width, height);
        graphics.setClip(previousClip);
    }

    private static void drawAlbumCard(Graphics2D graphics, PrintableCard card, int x, int y, int width, int height) {
        int horizontalPadding = Math.max(34, width / 14);
        int topPadding = Math.max(34, height / 18);
        int bottomPadding = Math.max(30, height / 18);
        int titleGap = Math.max(22, height / 36);
        int titleAreaHeight = Math.max(160, height / 3);
        int imageAreaHeight = Math.max(1, height - topPadding - bottomPadding - titleGap - titleAreaHeight);
        int imageSize = Math.max(1, Math.min(width - horizontalPadding * 2, imageAreaHeight));

        int imageX = x + (width - imageSize) / 2;
        int imageY = y + topPadding;
        int titleX = x + horizontalPadding;
        int titleY = imageY + imageSize + titleGap;
        int titleWidth = width - horizontalPadding * 2;

        graphics.setColor(new Color(246, 248, 252));
        graphics.fillRoundRect(imageX, imageY, imageSize, imageSize, 28, 28);

        Shape previousClip = graphics.getClip();
        graphics.setClip(new RoundRectangle2D.Float(imageX, imageY, imageSize, imageSize, 28, 28));
        drawCardImage(graphics, card.imagePath(), imageX, imageY, imageSize, imageSize);
        graphics.setClip(previousClip);

        graphics.setColor(new Color(228, 232, 239));
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.drawRoundRect(imageX, imageY, imageSize, imageSize, 28, 28);

        drawCardTitle(graphics, card.title(), titleX, titleY, titleWidth, titleAreaHeight);
    }

    private static void drawCardTitle(Graphics2D graphics, String title, int x, int y, int width, int height) {
        String normalizedTitle = title == null || title.isBlank() ? "Untitled" : title.trim();
        int maxFontSize = Math.max(34, width / 11);
        int minFontSize = Math.max(18, width / 20);
        TitleLayout titleLayout = null;

        for (int fontSize = maxFontSize; fontSize >= minFontSize; fontSize -= 2) {
            Font candidateFont = new Font("Microsoft YaHei", Font.BOLD, fontSize);
            TitleLayout candidateLayout = createTitleLayout(graphics, normalizedTitle, candidateFont, width, 4, false);
            if (candidateLayout.totalHeight() <= height && !candidateLayout.truncated()) {
                titleLayout = candidateLayout;
                break;
            }
            if (titleLayout == null || candidateLayout.totalHeight() <= height) {
                titleLayout = candidateLayout;
            }
        }

        if (titleLayout == null) {
            Font fallbackFont = new Font("Microsoft YaHei", Font.BOLD, minFontSize);
            titleLayout = createTitleLayout(graphics, normalizedTitle, fallbackFont, width, 4, true);
        } else if (titleLayout.totalHeight() > height || titleLayout.truncated()) {
            Font fallbackFont = titleLayout.font();
            titleLayout = createTitleLayout(graphics, normalizedTitle, fallbackFont, width, 4, true);
        }

        FontMetrics titleMetrics = graphics.getFontMetrics(titleLayout.font());
        int totalTextHeight = titleLayout.totalHeight();
        int currentY = y + Math.max(titleMetrics.getAscent(), (height - totalTextHeight) / 2 + titleMetrics.getAscent());

        graphics.setColor(TITLE_COLOR);
        graphics.setFont(titleLayout.font());
        for (String line : titleLayout.lines()) {
            int lineX = x + (width - titleMetrics.stringWidth(line)) / 2;
            graphics.drawString(line, lineX, currentY);
            currentY += titleMetrics.getHeight();
        }
    }

    private static void drawCardImage(Graphics2D graphics, Path imagePath, int x, int y, int width, int height) {
        try {
            BufferedImage sourceImage = loadImage(imagePath);
            if (sourceImage == null) {
                drawMissingImagePlaceholder(graphics, x, y, width, height);
                return;
            }

            BufferedImage normalizedImage = normalizeCardImage(sourceImage);
            drawCroppedImage(graphics, normalizedImage, x, y, width, height);
        } catch (Exception ignored) {
            drawMissingImagePlaceholder(graphics, x, y, width, height);
        }
    }

    private static BufferedImage normalizeCardImage(BufferedImage sourceImage) {
        if (sourceImage.getWidth() > sourceImage.getHeight()) {
            return rotateClockwise(sourceImage);
        }
        return sourceImage;
    }

    private static BufferedImage rotateClockwise(BufferedImage image) {
        BufferedImage rotated = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            configureGraphics(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rotated.getWidth(), rotated.getHeight());

            AffineTransform transform = new AffineTransform();
            transform.translate(rotated.getWidth(), 0);
            transform.rotate(Math.PI / 2);
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    private static void drawCroppedImage(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height) {
        double targetAspect = width / (double) height;
        double sourceAspect = image.getWidth() / (double) image.getHeight();

        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();

        if (sourceAspect > targetAspect) {
            sourceWidth = Math.max(1, (int) Math.round(image.getHeight() * targetAspect));
            sourceX = (image.getWidth() - sourceWidth) / 2;
        } else if (sourceAspect < targetAspect) {
            sourceHeight = Math.max(1, (int) Math.round(image.getWidth() / targetAspect));
            sourceY = (image.getHeight() - sourceHeight) / 2;
        }

        graphics.drawImage(
            image,
            x,
            y,
            x + width,
            y + height,
            sourceX,
            sourceY,
            sourceX + sourceWidth,
            sourceY + sourceHeight,
            null
        );
    }

    private static BufferedImage loadImage(Path imagePath) throws IOException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return null;
        }

        BufferedImage image = null;
        try {
            image = ImageIO.read(imagePath.toFile());
        } catch (IOException ignored) {
            image = null;
        }
        if (image != null) {
            return toRgbImage(image);
        }

        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            Image fxImage = new Image(inputStream);
            if (fxImage.isError() || fxImage.getPixelReader() == null) {
                return null;
            }
            return fromFxImage(fxImage);
        }
    }

    private static BufferedImage fromFxImage(Image image) {
        int width = Math.max(1, (int) Math.round(image.getWidth()));
        int height = Math.max(1, (int) Math.round(image.getHeight()));
        PixelReader pixelReader = image.getPixelReader();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixelReader.getArgb(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;

                int blendedRed = (red * alpha + 255 * (255 - alpha)) / 255;
                int blendedGreen = (green * alpha + 255 * (255 - alpha)) / 255;
                int blendedBlue = (blue * alpha + 255 * (255 - alpha)) / 255;
                int rgb = (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
                bufferedImage.setRGB(x, y, rgb);
            }
        }
        return bufferedImage;
    }

    private static BufferedImage toRgbImage(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            configureGraphics(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, converted.getWidth(), converted.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static void drawMissingImagePlaceholder(Graphics2D graphics, int x, int y, int width, int height) {
        graphics.setColor(new Color(232, 236, 242));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(new Color(210, 216, 225));
        graphics.drawRect(x, y, width - 1, height - 1);

        int iconSize = Math.max(120, Math.min(width, height) / 4);
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + height / 2 - iconSize;

        graphics.setStroke(new BasicStroke(8f));
        graphics.setColor(new Color(188, 196, 208));
        graphics.drawRoundRect(iconX, iconY, iconSize, iconSize, 18, 18);
        graphics.drawLine(iconX + iconSize / 5, iconY + iconSize * 4 / 5, iconX + iconSize * 2 / 5, iconY + iconSize * 3 / 5);
        graphics.drawLine(iconX + iconSize * 2 / 5, iconY + iconSize * 3 / 5, iconX + iconSize * 3 / 5, iconY + iconSize * 11 / 16);
        graphics.drawLine(iconX + iconSize * 3 / 5, iconY + iconSize * 11 / 16, iconX + iconSize * 4 / 5, iconY + iconSize / 3);

        Font font = new Font("Microsoft YaHei", Font.BOLD, Math.max(28, width / 18));
        graphics.setFont(font);
        graphics.setColor(PLACEHOLDER_COLOR);
        FontMetrics metrics = graphics.getFontMetrics(font);
        String text = "Image Missing";
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = iconY + iconSize + metrics.getHeight() + 12;
        graphics.drawString(text, textX, textY);
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("当前运行环境缺少 JPEG 编码器。");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ImageOutputStream imageStream = ImageIO.createImageOutputStream(byteStream)) {
            writer.setOutput(imageStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        return byteStream.toByteArray();
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeAscii(OutputStream outputStream, String value) throws IOException {
        outputStream.write(ascii(value));
    }

    enum PrintMode {
        STANDARD("标准打印模式"),
        ALBUM("专辑打印模式");

        private final String label;

        PrintMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    record PrintableCard(Path imagePath, String title) {
    }

    private record CardLayout(int startX, int startY, int cardWidth, int cardHeight) {
    }

    private record TitleLayout(Font font, List<String> lines, boolean truncated, int lineHeight) {
        private int totalHeight() {
            return lines.size() * lineHeight;
        }
    }

    private record WrappedLines(List<String> lines, boolean truncated) {
    }

    private static List<String> wrapText(Graphics2D graphics, String text, Font font, int maxWidth, int maxLines) {
        return wrapText(graphics, text, font, maxWidth, maxLines, true).lines();
    }

    private static TitleLayout createTitleLayout(
        Graphics2D graphics,
        String text,
        Font font,
        int maxWidth,
        int maxLines,
        boolean allowTruncation
    ) {
        WrappedLines wrappedLines = wrapText(graphics, text, font, maxWidth, maxLines, allowTruncation);
        FontMetrics metrics = graphics.getFontMetrics(font);
        return new TitleLayout(font, wrappedLines.lines(), wrappedLines.truncated(), metrics.getHeight());
    }

    private static WrappedLines wrapText(
        Graphics2D graphics,
        String text,
        Font font,
        int maxWidth,
        int maxLines,
        boolean allowTruncation
    ) {
        if (text == null || text.isBlank()) {
            return new WrappedLines(List.of(""), false);
        }

        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        String[] words = text.trim().split("\\s+");
        List<String> lines = new ArrayList<>();

        if (words.length == 1) {
            return breakLongWord(metrics, words[0], maxWidth, maxLines, allowTruncation);
        }

        String currentLine = "";
        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                currentLine = candidate;
                continue;
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
                if (lines.size() >= maxLines) {
                    return allowTruncation
                        ? truncateLastLine(metrics, lines, maxWidth)
                        : new WrappedLines(lines, true);
                }
                currentLine = word;
            } else {
                WrappedLines brokenWord = breakLongWord(metrics, word, maxWidth, maxLines - lines.size(), allowTruncation);
                lines.addAll(brokenWord.lines());
                if (lines.size() >= maxLines) {
                    return allowTruncation
                        ? truncateLastLine(metrics, lines, maxWidth)
                        : new WrappedLines(lines, true);
                }
                currentLine = "";
            }
        }

        if (!currentLine.isEmpty() && lines.size() < maxLines) {
            lines.add(currentLine);
        }
        if (allowTruncation) {
            return truncateLastLine(metrics, lines, maxWidth);
        }
        return new WrappedLines(lines, false);
    }

    private static WrappedLines breakLongWord(
        FontMetrics metrics,
        String word,
        int maxWidth,
        int maxLines,
        boolean allowTruncation
    ) {
        List<String> lines = new ArrayList<>();
        String remaining = word;
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            int splitIndex = remaining.length();
            while (splitIndex > 1 && metrics.stringWidth(remaining.substring(0, splitIndex)) > maxWidth) {
                splitIndex--;
            }
            lines.add(remaining.substring(0, splitIndex));
            remaining = remaining.substring(splitIndex);
        }
        if (allowTruncation) {
            return truncateLastLine(metrics, lines, maxWidth);
        }
        return new WrappedLines(lines, !remaining.isEmpty());
    }

    private static WrappedLines truncateLastLine(FontMetrics metrics, List<String> lines, int maxWidth) {
        if (lines.isEmpty()) {
            return new WrappedLines(lines, false);
        }
        int lastIndex = lines.size() - 1;
        String originalLastLine = lines.get(lastIndex);
        String lastLine = originalLastLine;
        String ellipsis = "...";
        while (!lastLine.isEmpty() && metrics.stringWidth(lastLine + ellipsis) > maxWidth) {
            lastLine = lastLine.substring(0, lastLine.length() - 1);
        }
        if (!lastLine.equals(originalLastLine)) {
            lines.set(lastIndex, lastLine + ellipsis);
            return new WrappedLines(lines, true);
        }
        return new WrappedLines(lines, false);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] value, int off, int len) throws IOException {
            out.write(value, off, len);
            count += len;
        }

        private long getCount() {
            return count;
        }
    }
}
