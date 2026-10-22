package io.github.luyingyilong.ore.gui;

import net.minecraft.resources.ResourceLocation;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

final class UiParser {
    private static final int MAX_DEPTH = 128, MAX_NODES = 10000, MAX_CHARS = 2_000_000;

    private UiParser() {
    }

    static UiBlueprint parse(ResourceLocation resource, Reader input) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Handler handler = new Handler(resource);
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are disabled");
            });
            reader.parse(new InputSource(new FilterReader(input) {
                private int count;

                @Override
                public int read(char[] chars, int offset, int length) throws IOException {
                    int size = super.read(chars, offset, Math.min(length, 8192));
                    if (size > 0 && (count += size) > MAX_CHARS) throw new IOException("XML exceeds size limit");
                    return size;
                }
            }));
            if (handler.root == null)
                throw new UiException(new UiBlueprint.Source(resource, 1, 1, ""), "Empty document");
            return new UiBlueprint(resource, handler.root);
        } catch (UiException error) {
            throw error;
        } catch (SAXParseException error) {
            throw new UiException(new UiBlueprint.Source(resource, error.getLineNumber(), error.getColumnNumber(), ""), error.getMessage(), error);
        } catch (Exception error) {
            throw new UiException(new UiBlueprint.Source(resource, 1, 1, ""), error.getMessage(), error);
        }
    }

    private static final class Builder {
        String name;
        Map<String, String> attrs = new LinkedHashMap<>();
        List<UiBlueprint.Node> children = new ArrayList<>();
        UiBlueprint.Source source;
    }

    private static final class Handler extends DefaultHandler {
        final ResourceLocation resource;
        final Deque<Builder> stack = new ArrayDeque<>();
        Locator locator;
        UiBlueprint.Node root;
        int count;

        Handler(ResourceLocation resource) {
            this.resource = resource;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String local, String name, Attributes attrs) {
            if (++count > MAX_NODES || stack.size() >= MAX_DEPTH)
                throw new UiException(source(""), "XML structure exceeds limit");
            Builder builder = new Builder();
            builder.name = name;
            for (int i = 0; i < attrs.getLength(); i++) builder.attrs.put(attrs.getQName(i), attrs.getValue(i));
            builder.source = source(builder.attrs.getOrDefault("id", ""));
            stack.push(builder);
        }

        UiBlueprint.Source source(String id) {
            return new UiBlueprint.Source(resource, locator.getLineNumber(), locator.getColumnNumber(), id);
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (!new String(chars, start, length).isBlank())
                throw new UiException(source(""), "Use attributes for text content");
        }

        @Override
        public void endElement(String uri, String local, String name) {
            Builder b = stack.pop();
            UiBlueprint.Node node = new UiBlueprint.Node(b.name, b.attrs, b.children, b.source);
            if (stack.isEmpty()) root = node;
            else stack.peek().children.add(node);
        }

        @Override
        public void error(SAXParseException error) throws SAXException {
            throw error;
        }

        @Override
        public void fatalError(SAXParseException error) throws SAXException {
            throw error;
        }
    }
}
