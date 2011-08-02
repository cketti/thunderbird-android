
package com.fsck.k9.mail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface Part {
    public void addHeader(String name, String value) throws MessagingException;

    public void removeHeader(String name) throws MessagingException;

    public void setHeader(String name, String value) throws MessagingException;

    public Body getBody();

    public String getContentType() throws MessagingException;

    public String getDisposition() throws MessagingException;

    public String getContentId() throws MessagingException;

    public String[] getHeader(String name) throws MessagingException;

    public int getSize();

    public boolean isMimeType(String mimeType) throws MessagingException;

    public String getMimeType() throws MessagingException;

    public void setBody(Body body) throws MessagingException;

    public void writeTo(OutputStream out) throws IOException, MessagingException;

    public List<Field> getHeaders();


    class Field {
        public final String name;
        public final String value;

        public Field(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            sb.append(name).append('=').append(value).append(')');
            return sb.toString();
        }
    }
}
