package com.fsck.k9.protocol.eas;


import com.fsck.k9.mail.MessagingException;


public class PolicyException extends MessagingException {
    private final Policy policy;


    PolicyException(Policy policy) {
        super("Policy must be enforced");

        this.policy = policy;
    }

    public Policy getPolicy() {
        return policy;
    }
}
