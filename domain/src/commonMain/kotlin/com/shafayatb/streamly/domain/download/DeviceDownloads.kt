package com.shafayatb.streamly.domain.download

/**
 * Every download on the device, whichever account saved it. Only [AccountDownloadRepository]
 * reads it; screens get the signed-in account's downloads through [DownloadRepository].
 */
public interface DeviceDownloads : DownloadRepository
