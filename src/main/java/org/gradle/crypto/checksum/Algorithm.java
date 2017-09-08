package org.gradle.crypto.checksum;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public enum Algorithm {
    SHA256(Hashing.sha256()),
    SHA384(Hashing.sha384()),
    SHA512(Hashing.sha512());

    private final HashFunction hashFunction;

    Algorithm(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    public HashFunction getHashFunction() {
        return hashFunction;
    }
}
