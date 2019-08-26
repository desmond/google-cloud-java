/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud;

import com.google.api.core.ApiFunction;
import com.google.api.core.InternalApi;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import java.io.Serializable;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class for Identity and Access Management (IAM) v3 policies supported.
 *
 * @see <a href="https://cloud.google.com/iam/reference/rest/v1/Policy">Policy</a>
 */

public final class PolicyV3 implements Serializable {

  // TODO(frankyn): Update serial because it's a copy of Policy.java
  private static final long serialVersionUID = -3348914530232544291L;

  private final List<Binding> bindings;
  private final String etag;
  private final int version;

  public abstract static class Marshaller<T> {

    @InternalApi("This class should only be extended within google-cloud-java")
    protected Marshaller() {}

    protected static final ApiFunction<String, Identity> IDENTITY_VALUE_OF_FUNCTION =
        new ApiFunction<String, Identity>() {
          @Override
          public Identity apply(String identityPb) {
            return Identity.valueOf(identityPb);
          }
        };
    protected static final ApiFunction<Identity, String> IDENTITY_STR_VALUE_FUNCTION =
        new ApiFunction<Identity, String>() {
          @Override
          public String apply(Identity identity) {
            return identity.strValue();
          }
        };

    protected abstract PolicyV3 fromPb(T policyPb);

    protected abstract T toPb(PolicyV3 policy);
  }

  public static class DefaultMarshaller extends Marshaller<com.google.iam.v1.Policy> {

    @Override
    protected PolicyV3 fromPb(com.google.iam.v1.Policy policyPb) {
      // TODO (frankyn): Update to support conditions.
      List<Binding> bindings = new ArrayList<>();
      for (com.google.iam.v1.Binding bindingPb : policyPb.getBindingsList()) {
        bindings.add(Binding.newBuilder()
                .setRole(Role.of(bindingPb.getRole()))
                .setIdentities(ImmutableSet.copyOf(
                            Lists.transform(
                                bindingPb.getMembersList(),
                                new Function<String, Identity>() {
                                  @Override
                                  public Identity apply(String s) {
                                    return IDENTITY_VALUE_OF_FUNCTION.apply(s);
                                  }
                                }))).build());
      }
      return newBuilder()
          .setBindings(bindings)
          .setEtag(
              policyPb.getEtag().isEmpty()
                  ? null
                  : BaseEncoding.base64().encode(policyPb.getEtag().toByteArray()))
          .setVersion(policyPb.getVersion())
          .build();
    }

    @Override
    protected com.google.iam.v1.Policy toPb(PolicyV3 policy) {
      com.google.iam.v1.Policy.Builder policyBuilder = com.google.iam.v1.Policy.newBuilder();
      List<com.google.iam.v1.Binding> bindingPbList = new LinkedList<>();
      for (Binding binding : policy.getBindings()) {
        com.google.iam.v1.Binding.Builder bindingBuilder = com.google.iam.v1.Binding.newBuilder();
        bindingBuilder.setRole(binding.getRole().getValue());
        bindingBuilder.addAllMembers(
            Lists.transform(
                new ArrayList<>(binding.getIdentities()),
                new Function<Identity, String>() {
                  @Override
                  public String apply(Identity identity) {
                    return IDENTITY_STR_VALUE_FUNCTION.apply(identity);
                  }
                }));
        bindingPbList.add(bindingBuilder.build());
      }
      policyBuilder.addAllBindings(bindingPbList);
      if (policy.etag != null) {
        policyBuilder.setEtag(ByteString.copyFrom(BaseEncoding.base64().decode(policy.etag)));
      }
      policyBuilder.setVersion(policy.version);
      return policyBuilder.build();
    }
  }

  /** A builder for {@code Policy} objects. */
  public static class Builder {

    private final List<Binding> bindings = new ArrayList<>();
    private String etag;
    private int version;

    @InternalApi("This class should only be extended within google-cloud-java")
    protected Builder() {}

    @InternalApi("This class should only be extended within google-cloud-java")
    protected Builder(PolicyV3 policy) {
      setBindings(policy.bindings);
      setEtag(policy.etag);
      setVersion(policy.version);
    }

    /**
     * Replaces the builder's list of bindings with the given list of bindings.
     *
     * @throws NullPointerException if the given map is null or contains any null keys or values
     * @throws IllegalArgumentException if any identities in the given map are null
     */
    public final Builder setBindings(List<Binding> bindings) {
      checkNotNull(bindings, "The provided list of bindings cannot be null.");
      for (Binding binding : bindings) {
        checkNotNull(binding.getRole().getValue(), "The role cannot be null.");
        Set<Identity> identities = binding.getIdentities();
        checkNotNull(identities, "A role cannot be assigned to a null set of identities.");
        checkArgument(!identities.contains(null), "Null identities are not permitted.");
      }
      this.bindings.clear();
      for (Binding binding : bindings) {
        Binding.Builder bindingBuilder = Binding.newBuilder();
        bindingBuilder.setRole(binding.getRole());
        for (Identity identity : binding.getIdentities()) {
          bindingBuilder.addIdentity(identity);
        }
        if (binding.getCondition() != null) {
          bindingBuilder.setCondition(Condition.newBuilder()
                  .setTitle(binding.getCondition().getTitle())
                  .setDescription(binding.getCondition().getDescription())
                  .setExpression(binding.getCondition().getExpression()).build());
        }
        this.bindings.add(bindingBuilder.build());
      }
      return this;
    }

    /**
     * Sets the policy's etag.
     *
     * <p>Etags are used for optimistic concurrency control as a way to help prevent simultaneous
     * updates of a policy from overwriting each other. It is strongly suggested that systems make
     * use of the etag in the read-modify-write cycle to perform policy updates in order to avoid
     * race conditions. An etag is returned in the response to getIamPolicy, and systems are
     * expected to put that etag in the request to setIamPolicy to ensure that their change will be
     * applied to the same version of the policy. If no etag is provided in the call to
     * setIamPolicy, then the existing policy is overwritten blindly.
     */
    public final Builder setEtag(String etag) {
      this.etag = etag;
      return this;
    }

    /**
     * Sets the version of the policy. The default version is 0, meaning only the "owner", "editor",
     * and "viewer" roles are permitted. If the version is 1, you may also use other roles.
     */
    protected final Builder setVersion(int version) {
      this.version = version;
      return this;
    }

    /** Creates a {@code Policy} object. */
    public final PolicyV3 build() {
      return new PolicyV3(this);
    }
  }

  private PolicyV3(Builder builder) {
    ImmutableList.Builder<Binding> bindingsBuilder = ImmutableList.builder();
    for (Binding binding : builder.bindings) {
      bindingsBuilder.add(binding);
    }
    this.bindings = bindingsBuilder.build();
    this.etag = builder.etag;
    this.version = builder.version;
  }

  /** Returns a builder containing the properties of this IAM Policy. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /** Returns the map of bindings that comprises the policy. */
  public List<Binding> getBindings() {
    return bindings;
  }

  /**
   * Returns the policy's etag.
   *
   * <p>Etags are used for optimistic concurrency control as a way to help prevent simultaneous
   * updates of a policy from overwriting each other. It is strongly suggested that systems make use
   * of the etag in the read-modify-write cycle to perform policy updates in order to avoid race
   * conditions. An etag is returned in the response to getIamPolicy, and systems are expected to
   * put that etag in the request to setIamPolicy to ensure that their change will be applied to the
   * same version of the policy. If no etag is provided in the call to setIamPolicy, then the
   * existing policy is overwritten blindly.
   */
  public String getEtag() {
    return etag;
  }

  /**
   * Returns the version of the policy. The default version is 0, meaning only the "owner",
   * "editor", and "viewer" roles are permitted. If the version is 1, you may also use other roles.
   */
  public int getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("bindings", bindings)
        .add("etag", etag)
        .add("version", version)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), bindings, etag, version);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof PolicyV3)) {
      return false;
    }
    PolicyV3 other = (PolicyV3) obj;
    return Objects.equals(bindings, other.getBindings())
        && Objects.equals(etag, other.getEtag())
        && Objects.equals(version, other.getVersion());
  }

  /** Returns a builder for {@code Policy} objects. */
  public static Builder newBuilder() {
    return new Builder();
  }
}
