package com.fortrow.jflux.runtime;

import javax.crypto.SecretKey;

/** Supplies a package key without persisting that key inside the package. */
@FunctionalInterface
public interface RuntimePackageKeyProvider {
    SecretKey keyFor(String keyId, String packageId, String version) throws Exception;
}
