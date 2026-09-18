package edu.gatech.cc.cellwatch.domain.sync

/**
 * Reports that no public address could be obtained, so the client sends none.
 *
 * FCC requires `server_source_ip_address` / `server_source_port` to be the
 * device's address and TCP port "as measured by the server" receiving the
 * submission, correlated with `server_timestamp` (BDC Data Specifications for
 * Mobile Speed Test Data, s5.1.2, Submission Object). Those are properties of
 * the device -> Supabase upload, so only Supabase can supply them: an address
 * fetched by the client from anywhere else describes a different TCP connection.
 *
 * Sending nothing is therefore the correct client behaviour. The column is left
 * NULL, which is what lets Supabase's `fcc_submission_update_source_ip` trigger
 * record the address it actually observed - a non-null value would suppress it,
 * which is how submissions previously ended up carrying an RFC 5737
 * documentation address (203.0.113.x) from a hardcoded stub.
 *
 * See doc/PRE_DEPLOYMENT_CHECKLIST.md for the unresolved `server_source_port`
 * gap: the port is not recoverable at a managed edge.
 */
object UnavailableTcpTupleProvider : TcpTupleProvider {
    override suspend fun getPublicTcpTuple(): Nothing =
        throw IllegalStateException("source address is measured by the receiving server, not the client")
}
