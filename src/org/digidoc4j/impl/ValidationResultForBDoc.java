package org.digidoc4j.impl;

import eu.europa.ec.markt.dss.DSSXMLUtils;
import eu.europa.ec.markt.dss.validation102853.report.Conclusion;
import eu.europa.ec.markt.dss.validation102853.report.Reports;
import eu.europa.ec.markt.dss.validation102853.report.SimpleReport;
import org.digidoc4j.ValidationResult;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Overview of errors and warnings for BDoc
 */

public class ValidationResultForBDoc implements ValidationResult {
  static final Logger logger = LoggerFactory.getLogger(ValidationResultForDDoc.class);
  private List<DigiDoc4JException> errors = new ArrayList<>();
  private List<DigiDoc4JException> warnings = new ArrayList<>();
  private Document reportDocument;

  /**
   * Constructor
   *
   * @param report add description
   */
  public ValidationResultForBDoc(Reports report) {
    logger.debug("");

    initializeReportDOM();

    do {
      SimpleReport simpleReport = report.getSimpleReport();

      String signatureId = simpleReport.getSignatureIds().get(0);

      List<Conclusion.BasicInfo> results = simpleReport.getErrors(signatureId);
      for (Conclusion.BasicInfo result : results) {
        String message = result.toString();
        logger.debug("Validation error: " + message);
        errors.add(new DigiDoc4JException(message));
      }
      results = simpleReport.getWarnings(signatureId);
      for (Conclusion.BasicInfo result : results) {
        String message = result.toString();
        logger.debug("Validation warning: " + message);
        warnings.add(new DigiDoc4JException(message));
      }

      createXMLReport(simpleReport);
      if (logger.isDebugEnabled()) {
        logger.debug(simpleReport.toString());
      }
      report = report.getNextReports();

    } while (report != null);
  }

  private void initializeReportDOM() {
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = null;
    try {
      docBuilder = docFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }

    reportDocument = docBuilder.newDocument();
    reportDocument.appendChild(reportDocument.createElement("ValidationReport"));
  }

  private void createXMLReport(SimpleReport simpleReport) {

    Element signatureValidation = reportDocument.createElement("SignatureValidation");
    signatureValidation.setAttribute("ID", simpleReport.getSignatureIds().get(0));
    reportDocument.getDocumentElement().appendChild(signatureValidation);

    Element rootElement = simpleReport.getRootElement();
    NodeList childNodes = rootElement.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node importNode = reportDocument.importNode(childNodes.item(i), true);

      signatureValidation.appendChild(importNode);
    }
  }

  @Override
  public List<DigiDoc4JException> getErrors() {
    return errors;
  }

  @Override
  public List<DigiDoc4JException> getWarnings() {
    return warnings;
  }

  @Override
  public boolean hasErrors() {
    return (errors.size() != 0);
  }

  @Override
  public boolean hasWarnings() {
    return (warnings.size() != 0);
  }

  @Override
  public boolean isValid() {
    return !hasErrors();
  }

  @Override
  public String getReport() {
    return new String(DSSXMLUtils.transformDomToByteArray(reportDocument));
  }
}
