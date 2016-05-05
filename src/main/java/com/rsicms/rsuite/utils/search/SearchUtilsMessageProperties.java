package com.rsicms.rsuite.utils.search;

import java.io.IOException;

import com.rsicms.rsuite.utils.messsageProps.LibraryMessageProperties;

/**
 * Serves up formatted messages from this libraries message properties file.
 */
public class SearchUtilsMessageProperties extends LibraryMessageProperties {

	public SearchUtilsMessageProperties() throws IOException {
		super(SearchUtilsMessageProperties.class);
	}

}
