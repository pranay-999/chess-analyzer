package com.chessanalyzer.game;

/**
 * What the sync endpoint returns (Spring turns it into JSON).
 *
 * @param monthsFetched  how many monthly archives we downloaded
 * @param imported       new games saved
 * @param alreadyStored  games we had from an earlier sync
 * @param skipped        variants (Chess960, bughouse...) we don't support
 * @param failed         games whose PGN could not be replayed
 */
public record SyncResult(String username, int monthsFetched, int imported,
                         int alreadyStored, int skipped, int failed) {
}
