package com.fsck.k9.mail.message;


import java.io.File;

import com.fsck.k9.mail.data.ContentBody;


public interface FileBackedBody extends ContentBody {
    File getFile();
}
