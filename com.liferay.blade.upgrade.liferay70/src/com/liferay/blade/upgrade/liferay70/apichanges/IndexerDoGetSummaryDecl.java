package com.liferay.blade.upgrade.liferay70.apichanges;

import com.liferay.blade.api.FileMigrator;
import com.liferay.blade.api.JavaFile;
import com.liferay.blade.api.SearchResult;
import com.liferay.blade.upgrade.liferay70.JavaFileMigrator;

import java.io.File;
import java.util.List;

import org.osgi.service.component.annotations.Component;

@Component(
	property = {
		"file.extensions=java",
		"problem.summary=Changed the Indexer API to Include the PortletRequest and PortletResponse Parameters",
		"problem.tickets=LPS-44639,LPS-44894",
		"problem.title=Indexer API Changes",
		"problem.section=#changed-the-assetrenderer-and-indexer-apis-to-include-the-portletrequest-and-portletresponse-parameters"
	},
	service = FileMigrator.class
)
public class IndexerDoGetSummaryDecl extends JavaFileMigrator {

	@Override
	protected List<SearchResult> searchFile(File file, JavaFile javaFileChecker) {
		return javaFileChecker.findMethodDeclaration("doGetSummary", new String[] {"Document", "Locale", "String", "PortletURL"});
	}
}