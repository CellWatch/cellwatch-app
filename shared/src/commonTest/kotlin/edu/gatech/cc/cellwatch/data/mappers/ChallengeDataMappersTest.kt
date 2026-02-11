package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.data.transport.NetworkChallengeData
import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

class ChallengeDataMappersTest {

    @Test
    fun toNetwork_maps_all_fields_correctly() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val createdOn = Instant.parse("2025-01-01T12:34:56Z")
        val updatedOn = Instant.parse("2025-01-01T12:35:56Z")

        val domain = ChallengeData(
            id = id,
            submissionCategory = "category-a",
            contactName = "Alice Example",
            contactEmail = "alice@example.com",
            contactPhone = "+1-555-0100",
            dataSharingAcknowledgement = true,
            createdOn = createdOn,
            updatedOn = updatedOn
        )

        val network = domain.toNetwork()

        assertEquals(id, network.id)
        assertEquals(domain.submissionCategory, network.submissionCategory)
        assertEquals(domain.contactName, network.contactName)
        assertEquals(domain.contactEmail, network.contactEmail)
        assertEquals(domain.contactPhone, network.contactPhone)
        assertEquals(domain.dataSharingAcknowledgement, network.dataSharingAcknowledgement)
        assertEquals(domain.createdOn, network.createdOn)
        assertEquals(domain.updatedOn, network.updatedOn)
    }

    @Test
    fun toDomain_maps_all_fields_correctly() {
        val idString = "123e4567-e89b-12d3-a456-426614174000"
        val createdOn = Instant.parse("2025-01-01T12:34:56Z")
        val updatedOn = Instant.parse("2025-01-01T12:35:56Z")

        val network = NetworkChallengeData(
            id = idString,
            submissionCategory = "category-b",
            contactName = "Bob Example",
            contactEmail = "bob@example.com",
            contactPhone = "+1-555-0200",
            dataSharingAcknowledgement = false,
            createdOn = createdOn,
            updatedOn = updatedOn
        )

        val domain = network.toDomain()

        assertEquals(idString, domain.id)
        assertEquals(network.submissionCategory, domain.submissionCategory)
        assertEquals(network.contactName, domain.contactName)
        assertEquals(network.contactEmail, domain.contactEmail)
        assertEquals(network.contactPhone, domain.contactPhone)
        assertEquals(network.dataSharingAcknowledgement, domain.dataSharingAcknowledgement)
        assertEquals(network.createdOn, domain.createdOn)
        assertEquals(network.updatedOn, domain.updatedOn)
    }

    @Test
    fun round_trip_domain_to_network_and_back_preserves_values() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val createdOn = Instant.parse("2025-01-01T12:34:56Z")
        val updatedOn = Instant.parse("2025-01-01T12:35:56Z")

        val original = ChallengeData(
            id = id,
            submissionCategory = "category-roundtrip",
            contactName = "Carol Roundtrip",
            contactEmail = "carol@example.com",
            contactPhone = "+1-555-0300",
            dataSharingAcknowledgement = true,
            createdOn = createdOn,
            updatedOn = updatedOn
        )

        val roundTripped = original
            .toNetwork()
            .toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.submissionCategory, roundTripped.submissionCategory)
        assertEquals(original.contactName, roundTripped.contactName)
        assertEquals(original.contactEmail, roundTripped.contactEmail)
        assertEquals(original.contactPhone, roundTripped.contactPhone)
        assertEquals(original.dataSharingAcknowledgement, roundTripped.dataSharingAcknowledgement)
        assertEquals(original.createdOn, roundTripped.createdOn)
        assertEquals(original.updatedOn, roundTripped.updatedOn)
    }

    @Test
    fun round_trip_domain_to_db_row_and_back_preserves_values() {
        val original = ChallengeData(
            id = "123e4567-e89b-12d3-a456-426614174000",
            submissionCategory = "category-db",
            contactName = "Dana DB",
            contactEmail = "dana@example.com",
            contactPhone = "+1-555-0400",
            dataSharingAcknowledgement = true,
            createdOn = Instant.parse("2025-01-01T12:34:56Z"),
            updatedOn = Instant.parse("2025-01-01T12:35:56Z"),
        )

        val roundTripped = original.toRow().toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.submissionCategory, roundTripped.submissionCategory)
        assertEquals(original.contactName, roundTripped.contactName)
        assertEquals(original.contactEmail, roundTripped.contactEmail)
        assertEquals(original.contactPhone, roundTripped.contactPhone)
        assertEquals(original.dataSharingAcknowledgement, roundTripped.dataSharingAcknowledgement)
        assertEquals(original.createdOn, roundTripped.createdOn)
        assertEquals(original.updatedOn, roundTripped.updatedOn)
    }
}
