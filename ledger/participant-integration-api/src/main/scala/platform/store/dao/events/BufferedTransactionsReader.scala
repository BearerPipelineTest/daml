// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.dao.events

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.daml.ledger.api.v1.active_contracts_service.GetActiveContractsResponse
import com.daml.ledger.api.v1.transaction_service.{
  GetFlatTransactionResponse,
  GetTransactionResponse,
  GetTransactionTreesResponse,
  GetTransactionsResponse,
}
import com.daml.ledger.offset.Offset
import com.daml.lf.data.Ref.TransactionId
import com.daml.logging.LoggingContext
import com.daml.metrics.Metrics
import com.daml.platform
import com.daml.platform.store.cache.InMemoryFanoutBuffer
import com.daml.platform.store.dao.BufferedStreamsReader.FetchFromPersistence
import com.daml.platform.store.dao.events.BufferedTransactionsReader.invertMapping
import com.daml.platform.store.dao.events.TransactionLogUpdatesConversions.{
  ToFlatTransaction,
  ToTransactionTree,
}
import com.daml.platform.store.dao.{
  BufferedStreamsReader,
  BufferedTransactionByIdReader,
  EventProjectionProperties,
  LedgerDaoTransactionsReader,
}
import com.daml.platform.store.interfaces.TransactionLogUpdate
import com.daml.platform.{FilterRelation, Identifier, Party}

import scala.concurrent.{ExecutionContext, Future}

private[events] class BufferedTransactionsReader(
    delegate: LedgerDaoTransactionsReader,
    bufferedFlatTransactionsReader: BufferedStreamsReader[
      (FilterRelation, EventProjectionProperties),
      GetTransactionsResponse,
    ],
    bufferedTransactionTreesReader: BufferedStreamsReader[
      (Set[Party], EventProjectionProperties),
      GetTransactionTreesResponse,
    ],
    bufferedFlatTransactionByIdReader: BufferedTransactionByIdReader[
      GetFlatTransactionResponse,
    ],
    bufferedTransactionTreeByIdReader: BufferedTransactionByIdReader[
      GetTransactionResponse,
    ],
    lfValueTranslation: LfValueTranslation,
)(implicit executionContext: ExecutionContext)
    extends LedgerDaoTransactionsReader {

  override def getFlatTransactions(
      startExclusive: Offset,
      endInclusive: Offset,
      filter: FilterRelation,
      eventProjectionProperties: EventProjectionProperties,
  )(implicit loggingContext: LoggingContext): Source[(Offset, GetTransactionsResponse), NotUsed] = {
    val (parties, partiesTemplates) = filter.partition(_._2.isEmpty)
    val wildcardParties = parties.keySet

    val templatesParties = invertMapping(partiesTemplates)
    val requestingParties = filter.keySet

    bufferedFlatTransactionsReader
      .stream(
        startExclusive = startExclusive,
        endInclusive = endInclusive,
        persistenceFetchArgs = (filter, eventProjectionProperties),
        bufferFilter =
          ToFlatTransaction.filter(wildcardParties, templatesParties, requestingParties),
        toApiResponse = ToFlatTransaction
          .toGetTransactionsResponse(filter, eventProjectionProperties, lfValueTranslation)(
            loggingContext,
            executionContext,
          ),
      )
  }

  override def getTransactionTrees(
      startExclusive: Offset,
      endInclusive: Offset,
      requestingParties: Set[Party],
      eventProjectionProperties: EventProjectionProperties,
  )(implicit
      loggingContext: LoggingContext
  ): Source[(Offset, GetTransactionTreesResponse), NotUsed] =
    bufferedTransactionTreesReader
      .stream(
        startExclusive = startExclusive,
        endInclusive = endInclusive,
        persistenceFetchArgs = (requestingParties, eventProjectionProperties),
        bufferFilter = ToTransactionTree.filter(requestingParties),
        toApiResponse = ToTransactionTree
          .toGetTransactionTreesResponse(
            requestingParties,
            eventProjectionProperties,
            lfValueTranslation,
          )(
            loggingContext,
            executionContext,
          ),
      )

  override def lookupFlatTransactionById(
      transactionId: TransactionId,
      requestingParties: Set[Party],
  )(implicit loggingContext: LoggingContext): Future[Option[GetFlatTransactionResponse]] =
    bufferedFlatTransactionByIdReader.fetch(transactionId, requestingParties)

  override def lookupTransactionTreeById(
      transactionId: TransactionId,
      requestingParties: Set[Party],
  )(implicit loggingContext: LoggingContext): Future[Option[GetTransactionResponse]] =
    bufferedTransactionTreeByIdReader.fetch(transactionId, requestingParties)

  override def getActiveContracts(
      activeAt: Offset,
      filter: FilterRelation,
      eventProjectionProperties: EventProjectionProperties,
  )(implicit
      loggingContext: LoggingContext
  ): Source[GetActiveContractsResponse, NotUsed] =
    delegate.getActiveContracts(activeAt, filter, eventProjectionProperties)
}

private[platform] object BufferedTransactionsReader {
  def apply(
      delegate: LedgerDaoTransactionsReader,
      transactionsBuffer: InMemoryFanoutBuffer,
      eventProcessingParallelism: Int,
      lfValueTranslation: LfValueTranslation,
      metrics: Metrics,
  )(implicit
      executionContext: ExecutionContext
  ): BufferedTransactionsReader = {
    val flatTransactionsStreamReader =
      new BufferedStreamsReader[
        (FilterRelation, EventProjectionProperties),
        GetTransactionsResponse,
      ](
        inMemoryFanoutBuffer = transactionsBuffer,
        fetchFromPersistence = new FetchFromPersistence[
          (FilterRelation, EventProjectionProperties),
          GetTransactionsResponse,
        ] {
          override def apply(
              startExclusive: Offset,
              endInclusive: Offset,
              filter: (FilterRelation, EventProjectionProperties),
          )(implicit
              loggingContext: LoggingContext
          ): Source[(Offset, GetTransactionsResponse), NotUsed] = {
            val (filterRelation, eventProjectionProperties) = filter
            delegate.getFlatTransactions(
              startExclusive,
              endInclusive,
              filterRelation,
              eventProjectionProperties,
            )
          }
        },
        bufferedStreamEventsProcessingParallelism = eventProcessingParallelism,
        metrics = metrics,
        streamName = "transactions",
      )

    val transactionTreesStreamReader =
      new BufferedStreamsReader[
        (Set[Party], EventProjectionProperties),
        GetTransactionTreesResponse,
      ](
        inMemoryFanoutBuffer = transactionsBuffer,
        fetchFromPersistence = new FetchFromPersistence[
          (Set[Party], EventProjectionProperties),
          GetTransactionTreesResponse,
        ] {
          override def apply(
              startExclusive: Offset,
              endInclusive: Offset,
              filter: (Set[Party], EventProjectionProperties),
          )(implicit
              loggingContext: LoggingContext
          ): Source[(Offset, GetTransactionTreesResponse), NotUsed] = {
            val (requestingParties, eventProjectionProperties) = filter
            delegate.getTransactionTrees(
              startExclusive,
              endInclusive,
              requestingParties,
              eventProjectionProperties,
            )
          }
        },
        bufferedStreamEventsProcessingParallelism = eventProcessingParallelism,
        metrics = metrics,
        streamName = "transaction_trees",
      )

    val bufferedFlatTransactionByIdReader =
      new BufferedTransactionByIdReader[GetFlatTransactionResponse](
        inMemoryFanoutBuffer = transactionsBuffer,
        fetchFromPersistence =
          (transactionId: String, requestingParties: Set[Party], loggingContext: LoggingContext) =>
            delegate.lookupFlatTransactionById(
              platform.TransactionId.assertFromString(transactionId),
              requestingParties,
            )(loggingContext),
        toApiResponse = (
            transactionAccepted: TransactionLogUpdate.TransactionAccepted,
            requestingParties: Set[Party],
            loggingContext: LoggingContext,
        ) =>
          ToFlatTransaction.toGetFlatTransactionResponse(
            transactionAccepted,
            requestingParties,
            lfValueTranslation,
          )(loggingContext, executionContext),
      )

    val bufferedTransactionTreeByIdReader =
      new BufferedTransactionByIdReader[GetTransactionResponse](
        inMemoryFanoutBuffer = transactionsBuffer,
        fetchFromPersistence =
          (transactionId: String, requestingParties: Set[Party], loggingContext: LoggingContext) =>
            delegate.lookupTransactionTreeById(
              platform.TransactionId.assertFromString(transactionId),
              requestingParties,
            )(loggingContext),
        toApiResponse = (
            transactionAccepted: TransactionLogUpdate.TransactionAccepted,
            requestingParties: Set[Party],
            loggingContext: LoggingContext,
        ) =>
          ToTransactionTree.toGetTransactionResponse(
            transactionAccepted,
            requestingParties,
            lfValueTranslation,
          )(loggingContext, executionContext),
      )

    new BufferedTransactionsReader(
      delegate = delegate,
      bufferedFlatTransactionsReader = flatTransactionsStreamReader,
      bufferedTransactionTreesReader = transactionTreesStreamReader,
      lfValueTranslation = lfValueTranslation,
      bufferedFlatTransactionByIdReader = bufferedFlatTransactionByIdReader,
      bufferedTransactionTreeByIdReader = bufferedTransactionTreeByIdReader,
    )
  }

  private[events] def invertMapping(
      partiesTemplates: Map[Party, Set[Identifier]]
  ): Map[Identifier, Set[Party]] =
    partiesTemplates
      .foldLeft(Map.empty[Identifier, Set[Party]]) {
        case (templatesToParties, (party, templates)) =>
          templates.foldLeft(templatesToParties) { case (aux, templateId) =>
            aux.updatedWith(templateId) {
              case None => Some(Set(party))
              case Some(partySet) => Some(partySet + party)
            }
          }
      }
}
