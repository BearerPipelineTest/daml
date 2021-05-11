// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state

import com.daml.lf.data.Ref
import com.daml.lf.transaction
import com.daml.lf.value.Value

/** Interfaces to read from and write to an (abstract) participant state.
  *
  * A DAML ledger participant is code that allows to actively participate in
  * the evolution of a shared Daml ledger. Each such participant maintains a
  * particular view onto the state of the Daml ledger. We call this view the
  * participant state.
  *
  * Actual implementations of a Daml ledger participant will likely maintain
  * more state than what is exposed through the interfaces in this package,
  * which is why we talk about an abstract participant state. It abstracts
  * over the different implementations of Daml ledger participants.
  *
  * The interfaces are optimized for easy implementation. The
  * [[v2.WriteService]] interface contains the methods for changing the
  * participant state (and potentially the state of the Daml ledger), which
  * all ledger participants must support. These methods are for example
  * exposed via the DAML Ledger API. Actual ledger participant implementations
  * likely support more implementation-specific methods. They are however not
  * exposed via the Daml Ledger API. The [[v2.ReadService]] interface contains
  * the one method [[v2.ReadService.stateUpdates]] to read the state of a ledger
  * participant. It represents the participant state as a stream of
  * [[v2.Update]]s to an initial participant state. The typical consumer of this
  * method is a class that subscribes to this stream of [[v2.Update]]s and
  * reconstructs (a view of) the actual participant state. See the comments
  * on [[v2.Update]] and [[v2.ReadService.stateUpdates]] for details about the kind
  * of updates and the guarantees given to consumers of the stream of
  * [[v2.Update]]s.
  *
  * We do expect the interfaces provided in
  * [[com.daml.ledger.participant.state]] to evolve, which is why we
  * provide them all in the
  * [[com.daml.ledger.participant.state.v2]] package.  Where possible
  * we will evolve them in a backwards compatible fashion, so that a simple
  * recompile suffices to upgrade to a new version. Where that is not
  * possible, we plan to introduce new version of this API in a separate
  * package and maintain it side-by-side with the existing version if
  * possible. There can therefore potentially be multiple versions of
  * participant state APIs at the same time. We plan to deprecate and drop old
  * versions on separate and appropriate timelines.
  */
package object v2 {

  /** Identifier for the ledger, MUST match regexp [a-zA-Z0-9-]. */
  type LedgerId = String

  /** Identifier for the participant, MUST match regexp [a-zA-Z0-9-]. */
  val ParticipantId: Ref.ParticipantId.type = Ref.ParticipantId
  type ParticipantId = Ref.ParticipantId

  /** Identifiers for transactions. */
  val TransactionId: Ref.LedgerString.type = Ref.LedgerString
  type TransactionId = Ref.LedgerString

  /** Identifiers used to correlate submission with results. */
  val CommandId: Ref.LedgerString.type = Ref.LedgerString
  type CommandId = Ref.LedgerString

  /** Identifiers used for correlating submission with a workflow. */
  val WorkflowId: Ref.LedgerString.type = Ref.LedgerString
  type WorkflowId = Ref.LedgerString

  /** Identifiers for submitting client applications. */
  val ApplicationId: Ref.LedgerString.type = Ref.LedgerString
  type ApplicationId = Ref.LedgerString

  /** Identifiers used to correlate admin submission with results. */
  val AdminSubmissionId: Ref.LedgerString.type = Ref.LedgerString
  type AdminSubmissionId = Ref.LedgerString

  /** Identifier for command submissions. */
  // TODO(v2) Should we restrict this to UUIDs?
  val SubmissionId: Ref.LedgerString.type = Ref.LedgerString
  type SubmissionId = Ref.LedgerString

  /** Identifiers for nodes in a transaction. */
  type NodeId = transaction.NodeId

  /** Identifiers for packages. */
  type PackageId = Ref.PackageId

  /** Identifiers for parties. */
  type Party = Ref.Party

  /** A transaction with contract IDs that may require suffixing.
    *
    * See the Contract Id specification for more detail daml-lf/spec/contract-id.rst
    */
  type SubmittedTransaction = transaction.SubmittedTransaction

  /** A transaction with globally unique contract IDs.
    *
    * Used to communicate transactions that have been accepted to the ledger.
    * See the Contract Id specification for more detail daml-lf/spec/contract-id.rst
    */
  type CommittedTransaction = transaction.CommittedTransaction

  /** A contract instance. */
  type ContractInst =
    Value.ContractInst[Value.VersionedValue[Value.ContractId]]

}
