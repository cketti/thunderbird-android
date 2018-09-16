package com.fsck.k9.mail.message;


import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.mail.data.Body;
import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.Part;
import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;


public enum TransferEncoding {
    NONE {
        @Override
        public InputStream decode(InputStream raw) {
            return raw;
        }
    },
    SEVEN_BIT {
        @Override
        public InputStream decode(InputStream raw) {
            return raw;
        }
    },
    EIGHT_BIT {
        @Override
        public InputStream decode(InputStream raw) {
            return raw;
        }
    },
    BINARY {
        @Override
        public InputStream decode(InputStream raw) {
            return raw;
        }
    },
    BASE_64 {
        @Override
        public InputStream decode(InputStream raw) {
            return new ClosingBase64InputStream(raw);
        }
    },
    QUOTED_PRINTABLE {
        @Override
        public InputStream decode(InputStream raw) {
            return new ClosingQuotedPrintableInputStream(raw);
        }
    };

    public abstract InputStream decode(InputStream raw);


    public static InputStream decode(Part part) {
        String transferEncodingValue = part.header().value("Content-Transfer-Encoding");
        TransferEncoding transferEncoding = fromString(transferEncodingValue);

        Body body = part.body();
        if (!(body instanceof ContentBody)) {
            throw new IllegalStateException("Body needs to be a ContentBody");
        }

        ContentBody contentBody = (ContentBody) body;
        InputStream raw = contentBody.raw();
        return transferEncoding.decode(raw);
    }

    public static TransferEncoding fromString(String transferEncoding) {
        if (transferEncoding == null) {
            return NONE;
        } else if ("7bit".equalsIgnoreCase(transferEncoding)) {
            return SEVEN_BIT;
        } else if ("8bit".equalsIgnoreCase(transferEncoding)) {
            return EIGHT_BIT;
        } else if ("binary".equalsIgnoreCase(transferEncoding)) {
            return BINARY;
        } else if ("base64".equalsIgnoreCase(transferEncoding)) {
            return BASE_64;
        } else if ("quoted-printable".equalsIgnoreCase(transferEncoding)) {
            return QUOTED_PRINTABLE;
        } else {
            throw new IllegalStateException("Unknown transfer encoding: " + transferEncoding);
        }
    }


    private static class ClosingBase64InputStream extends Base64InputStream {
        private final InputStream inputStream;

        public ClosingBase64InputStream(InputStream inputStream) {
            super(inputStream, false);
            this.inputStream = inputStream;
        }

        @Override
        public void close() throws IOException {
            super.close();
            inputStream.close();
        }
    }

    private static class ClosingQuotedPrintableInputStream extends QuotedPrintableInputStream {
        private final InputStream inputStream;

        public ClosingQuotedPrintableInputStream(InputStream inputStream) {
            super(inputStream, false);
            this.inputStream = inputStream;
        }

        @Override
        public void close() throws IOException {
            super.close();
            inputStream.close();
        }
    }
}
