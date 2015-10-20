package com.fsck.k9.mail.data.builder;


import com.fsck.k9.mail.data.Message;


public interface MessageBuilder extends PartBuilder, BodyBuilder {
    Message build();
}
