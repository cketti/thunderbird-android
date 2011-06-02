package com.fsck.k9.mail;

import java.io.IOException;
import java.io.OutputStream;

public interface Header {
	public void writeTo(OutputStream out) throws IOException;
}
