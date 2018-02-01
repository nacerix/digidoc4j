/* DigiDoc4J library
*
* This software is released under either the GNU Library General Public
* License (see LICENSE.LGPL).
*
* Note that the only valid version of the LGPL license as far as this
* project is concerned is the original GNU Library General Public License
* Version 2.1, February 1999
*/

package org.digidoc4j;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.digidoc4j.Constant.BDOC_CONTAINER_TYPE;
import static org.digidoc4j.Constant.DDOC_CONTAINER_TYPE;
import static org.digidoc4j.Container.DocumentType.BDOC;
import static org.digidoc4j.Container.DocumentType.DDOC;
import static org.digidoc4j.X509Cert.SubjectName.GIVENNAME;
import static org.digidoc4j.X509Cert.SubjectName.SERIALNUMBER;
import static org.digidoc4j.X509Cert.SubjectName.SURNAME;
import static org.digidoc4j.testutils.TestHelpers.containsErrorMessage;
import static org.digidoc4j.utils.DateUtils.isAlmostNow;
import static org.digidoc4j.utils.Helper.deleteFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.tsp.TimeStampToken;
import org.digidoc4j.exceptions.CertificateNotFoundException;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.exceptions.NotYetImplementedException;
import org.digidoc4j.impl.Certificates;
import org.digidoc4j.impl.DigiDoc4JTestHelper;
import org.digidoc4j.impl.asic.SKCommonCertificateVerifier;
import org.digidoc4j.impl.asic.ocsp.OcspSourceBuilder;
import org.digidoc4j.impl.asic.tsl.TSLCertificateSourceImpl;
import org.digidoc4j.impl.asic.tsl.TslManager;
import org.digidoc4j.impl.ddoc.DDocFacade;
import org.digidoc4j.impl.ddoc.DDocOpener;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.digidoc4j.testutils.TSLHelper;
import org.digidoc4j.testutils.TestDataBuilder;
import org.digidoc4j.utils.Helper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DSSUtils;
import eu.europa.esig.dss.FileDocument;
import eu.europa.esig.dss.client.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.client.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.client.tsp.OnlineTSPSource;
import eu.europa.esig.dss.validation.SignatureQualification;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.policy.rules.Indication;
import eu.europa.esig.dss.validation.policy.rules.SubIndication;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.validation.reports.SimpleReport;
import eu.europa.esig.dss.x509.ocsp.OCSPSource;

public class SignatureTest extends DigiDoc4JTestHelper {

  private PKCS12SignatureToken PKCS12_SIGNER;
  private PKCS12SignatureToken PKCS12_ECC_SIGNER;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    PKCS12_SIGNER = new PKCS12SignatureToken("src/test/resources/testFiles/p12/signout.p12", "test".toCharArray());
    PKCS12_ECC_SIGNER = new PKCS12SignatureToken("src/test/resources/testFiles/p12/MadDogOY.p12", "test".toCharArray());
  }

  @Test
  public void testEE_TSPWhenSigning() {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_tsp_source.yaml");
    Container container = ContainerBuilder.aContainer().withConfiguration(configuration).build();
    container.addDataFile("src/test/resources/testFiles/helper-files/test.txt", "text/plain");
    Signature signature = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
        .withSignatureToken(this.PKCS12_SIGNER).invokeSigning();
    Assert.assertEquals("Different country", "EE", signature.getSigningCertificate().getSubjectName(X509Cert.SubjectName.C));
    container.addSignature(signature);
    Assert.assertTrue("Container invalid", container.validate().isValid());
  }

  @Test
  @Ignore // TODO when we have LT signing certificate and working tsa url
  public void testLT_TSPWhenSigning() {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    configuration.loadConfiguration("src/test/resources/testFiles/yaml-configurations/digidoc_test_conf_tsp_source.yaml");
    Container container = ContainerOpener.open("src/test/resources/testFiles/valid-containers/latvian_signed_container.edoc", configuration);
    //Signature signature = SignatureBuilder.aSignature(container).withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
    //.withSignatureToken(this.PKCS12_SIGNER).invokeSigning();
    //Assert.assertEquals("Different country", "LT", signature.getSigningCertificate().getSubjectName(X509Cert.SubjectName.C));
    List<Signature> signatures = container.getSignatures();
    Assert.assertTrue("Container invalid", container.validate().isValid());
  }

  @Test
  public void signatureLTTSA(){
    OnlineTSPSource tspSource = new OnlineTSPSource("http://demo.sk.ee/tsa/");
    tspSource.setPolicyOid("0.4.0.2023.1.1");
    tspSource.setDataLoader(new TimestampDataLoader()); // content-type is different
    byte[] digest = DSSUtils.digest(eu.europa.esig.dss.DigestAlgorithm.SHA512, "Hello world".getBytes());
    TimeStampToken timeStampResponse = tspSource.getTimeStampResponse(eu.europa.esig.dss.DigestAlgorithm.SHA512, digest);
    Assert.assertNotNull(timeStampResponse);
  }

  @Test
  public void testGetSigningCertificateForBDoc() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/asics_for_testing.bdoc");
    byte[] certificate = container.getSignatures().get(0).getSigningCertificate().getX509Certificate().getEncoded();
    assertEquals(Certificates.SIGNING_CERTIFICATE, Base64.encodeBase64String(certificate));
  }

  @Test
  public void testTimeStampCreationTimeForBDoc() throws ParseException {
    Container container = ContainerOpener.open("src/test/resources/testFiles/valid-containers/test.asice");
    Date timeStampCreationTime = container.getSignature(0).getTimeStampCreationTime();
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy H:m:s", Locale.ENGLISH);
    assertEquals(dateFormat.parse("Nov 17 2014 16:11:46"), timeStampCreationTime);
  }

  @Test(expected = DigiDoc4JException.class)
  public void testTimeStampCreationTimeForDDoc() throws ParseException {
    Container container = createDDoc();
    container.addDataFile("src/test/resources/testFiles/helper-files/test.txt", "text/plain");
    container.sign(PKCS12_SIGNER);
    container.getSignature(0).getTimeStampCreationTime();
    container.getSignature(0).getTimeStampCreationTime();
  }

  @Test
  public void testTimeStampCreationTimeForBDocWhereNotOCSP() throws ParseException, IOException {
    Signature signature = createSignatureFor(BDOC_CONTAINER_TYPE, SignatureProfile.B_BES);
    assertNull(signature.getTimeStampCreationTime());
  }

  @Test
  public void testGetTimeStampTokenCertificateForBDoc() throws Exception {
    Signature signature = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc").getSignatures().get(0);
    byte[] certificate = signature.getTimeStampTokenCertificate().getX509Certificate().getEncoded();
    assertEquals(Certificates.TS_CERTIFICATE, Base64.encodeBase64String(certificate));
  }

  @Test
  public void testGetTimeStampTokenCertificateForBDocNoTimeStampExists() throws Exception {
    Signature signature = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/asics_for_testing.bdoc").getSignatures().get(0);
    assertNull(signature.getTimeStampTokenCertificate());
  }

  @Test(expected = CertificateNotFoundException.class)
  public void testGetSignerRolesForBDoc_OCSP_Exception() {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    List<Signature> signatures = container.getSignatures();
    assertNull(signatures.get(0).getOCSPCertificate());
  }

  @Test
  public void testGetSigningTimeForDDOC() {
    testGetSigningTime(DDOC);
  }

  @Test
  public void testGetSigningTimeForBDoc() {
    testGetSigningTime(BDOC);
  }

  private void testGetSigningTime(Container.DocumentType ddoc) {
    Signature signature = getSignature(ddoc);
    assertTrue(isAlmostNow(signature.getClaimedSigningTime()));
  }

  @Test
  public void testGetIdForDDOC() {
    Signature signature = getSignature(DDOC);
    assertEquals("S0", signature.getId());
  }

  @Test
  public void testGetIdForBDoc() {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    assertEquals("id-99E491801522116744419D9357CEFCC5", container.getSignatures().get(0).getId());
  }

  @Test
  public void testGetNonce() {
    Signature signature = getSignature(DDOC);
    assertEquals(null, Base64.encodeBase64String(signature.getOCSPNonce())); //todo correct nonce is needed
  }

  @Test
  public void testGetOCSPCertificateForDDoc() throws CertificateEncodingException {
    testGetOCSPCertificate(getSignature(DDOC));
  }

  @Test
  public void testGetOCSPCertificateForBDoc() throws CertificateEncodingException {
    testGetOCSPCertificate(getSignature(BDOC));
  }

  private void testGetOCSPCertificate(Signature signature) throws CertificateEncodingException {
    byte[] encoded = signature.getOCSPCertificate().getX509Certificate().getEncoded();
    assertEquals(Certificates.OCSP_CERTIFICATE, Base64.encodeBase64String(encoded));
  }

  @Test
  public void testGetSignaturePolicyForDDoc() {
    assertEquals("", getSignature(DDOC).getPolicy());
  }

  @Test(expected = NotYetImplementedException.class)
  public void testGetSignaturePolicyForBDoc() throws Exception {
    Signature signature = getSignature(BDOC);
    assertEquals("", signature.getPolicy());
  }

  @Test
  public void testGetProducedAtForDDoc() {
    assertTrue(isAlmostNow(getSignature(DDOC).getProducedAt()));
  }

  @Test
  public void testGetProducedAtForBDoc() throws ParseException {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse("2014-07-08 12:51:16 +0000");
    assertEquals(date, container.getSignatures().get(0).getProducedAt());
  }

  @Test
  public void testValidationForDDoc() {
    assertEquals(0, getSignature(DDOC).validateSignature().getErrors().size());
  }

  @Test
  public void testValidationNoParametersForDDoc() {
    assertEquals(0, getSignature(DDOC).validateSignature().getErrors().size());
  }

  @Test
  public void testValidationForBDocDefaultValidation() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    TSLHelper.addSkTsaCertificateToTsl(configuration);
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/two_signatures.bdoc", configuration);
    Signature signature = container.getSignatures().get(0);
    assertEquals(0, signature.validateSignature().getErrors().size());
    signature = container.getSignatures().get(1);
    assertEquals(0, signature.validateSignature().getErrors().size());
  }

  @Test
  public void testValidationForBDocDefaultValidationWithFailure() throws Exception {
    Signature signature = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc").getSignatures().get(0);
    List<DigiDoc4JException> errors = signature.validateSignature().getErrors();
    assertTrue(containsErrorMessage(errors, "The reference data object(s) is not intact!"));
    assertTrue(containsErrorMessage(errors, "Signature has an invalid timestamp"));
  }

  @Test
  public void testValidationForBDocDefaultValidationWithOneFailing() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/two_signatures_one_invalid.bdoc");
    Signature signature = container.getSignatures().get(0);
    assertEquals(0, signature.validateSignature().getErrors().size());
    signature = container.getSignatures().get(1);
    assertEquals(1, signature.validateSignature().getErrors().size());
    ValidationResult validate = container.validate();
    assertEquals(1, validate.getErrors().size());

    String report = validate.getReport();
    assertTrue(report.contains("Id=\"S0\" SignatureFormat=\"XAdES-BASELINE-LT\""));
    assertTrue(report.contains("Id=\"S1\" SignatureFormat=\"XAdES-BASELINE-LT\""));
    assertTrue(report.contains("<Indication>TOTAL_PASSED</Indication>"));
    assertTrue(report.contains("<Indication>INDETERMINATE</Indication>"));
  }

  @Test
  public void testValidationWithInvalidDDoc() {
    Signature signature = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/changed_digidoc_test.ddoc").getSignatures().get(0);
    assertEquals(4, signature.validateSignature().getErrors().size());
  }

  @Test
  public void testGetSignaturePolicyURIForDDoc() {
    assertNull(getSignature(DDOC).getSignaturePolicyURI());
  }

  @Test(expected = NotYetImplementedException.class)
  public void testGetSignaturePolicyURIForBDoc() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    assertEquals(new URI(""), container.getSignatures().get(0).getSignaturePolicyURI());
  }

  @Test
  public void testGetSignatureMethodDDoc() {
    assertEquals("http://www.w3.org/2000/09/xmldsig#rsa-sha1", getSignature(DDOC).getSignatureMethod());
  }

  @Test
  public void testGetSignatureMethodForBDoc() {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    assertEquals("http://www.w3.org/2001/04/xmlenc#sha256",
        container.getSignatures().get(0).getSignatureMethod());
  }

  @Test
  public void testGetProfileForDDoc() {
    assertEquals(SignatureProfile.LT_TM, getSignature(DDOC).getProfile());
  }

  @Test
  public void testGetProfileForBDoc_TS() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/ocsp_cert_is_not_in_tsl.bdoc");
    assertEquals(SignatureProfile.LT, container.getSignatures().get(0).getProfile());
  }

  @Test
  public void testGetProfileForBDoc_None() throws Exception {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/asics_for_testing.bdoc");
    assertEquals(SignatureProfile.B_BES, container.getSignatures().get(0).getProfile());
  }

  @Test(expected = NotYetImplementedException.class)
  public void testGetTimeStampTokenCertificateForDDoc() {
    assertNull(getSignature(DDOC).getTimeStampTokenCertificate());
  }

  @Test(expected = NotYetImplementedException.class)
  public void testGetNonceForBDoc() {
    Container container = ContainerOpener.open("src/test/resources/testFiles/invalid-containers/asics_for_testing.bdoc");
    container.getSignatures().get(0).getOCSPNonce();
  }

  @Test
  public void testGetSignaturesWhereNoSignaturePresent() throws Exception {
    DDocFacade container = new DDocFacade();
    assertTrue(container.getSignatures().isEmpty());
  }

  @Test
  public void testGetSignaturesWhereSignatureDoesNotHaveLastCertificate() throws Exception {
    Container container = new DDocOpener().open("src/test/resources/testFiles/invalid-containers/signature_without_last_certificate.ddoc");
    assertEquals(0, container.getSignatures().size());
  }

  @Test
  public void getSignatureXMLForBDOC() throws Exception {
    Container container = ContainerBuilder.
        aContainer().
        withDataFile("src/test/resources/testFiles/helper-files/test.txt", "text/plain").
        build();

    Signature signature = SignatureBuilder.
        aSignature(container).
        withSignatureToken(PKCS12_SIGNER).
        invokeSigning();
    container.addSignature(signature);

    container.saveAsFile("getSignatureXMLForBDOC.bdoc");
    String signatureFromContainer = Helper.extractSignature("getSignatureXMLForBDOC.bdoc", 0);


    deleteFile("getSignatureXMLForBDOC.bdoc");

    assertXMLEqual(signatureFromContainer, new String(signature.getAdESSignature()));
  }

  @Test
  public void signature_withoutProductionPlace_shouldNotThrowException() throws Exception {
    Container bdocContainer = TestDataBuilder.createContainerWithFile(testFolder, BDOC_CONTAINER_TYPE);
    Container ddocContainer = TestDataBuilder.createContainerWithFile(testFolder, DDOC_CONTAINER_TYPE);
    verifySignatureWithoutProductionPlaceDoesntThrow(bdocContainer);
    verifySignatureWithoutProductionPlaceDoesntThrow(ddocContainer);
  }

  @Test
  public void bDocBESSignature_TrustedSigningTime_shouldReturnNull() throws Exception {
    Signature signature = createSignatureFor(BDOC_CONTAINER_TYPE, SignatureProfile.B_BES);
    assertNull(signature.getTrustedSigningTime());
  }

  @Test
  public void dDocBESSignature_TrustedSigningTime_shouldReturnNull() throws Exception {
    Signature signature = createSignatureFor(DDOC_CONTAINER_TYPE, SignatureProfile.B_BES);
    assertNull(signature.getTrustedSigningTime());
  }

  @Test
  public void bDocTimeMarkSignature_TrustedSigningTime_shouldReturnOCSPResponseCreationTime() throws Exception {
    Signature signature = createSignatureFor(BDOC_CONTAINER_TYPE, SignatureProfile.LT_TM);
    assertNotNull(signature.getTrustedSigningTime());
    assertEquals(signature.getOCSPResponseCreationTime(), signature.getTrustedSigningTime());
  }

  @Test
  public void dDocTimeMarkSignature_TrustedSigningTime_shouldReturnOCSPResponseCreationTime() throws Exception {
    Signature signature = createSignatureFor(DDOC_CONTAINER_TYPE, SignatureProfile.LT_TM);
    assertNotNull(signature.getTrustedSigningTime());
    assertEquals(signature.getOCSPResponseCreationTime(), signature.getTrustedSigningTime());
  }

  @Test
  public void bDocTimeStampSignature_TrustedSigningTime_shouldReturnTimeStampCreationTime() throws Exception {
    Signature signature = createSignatureFor(BDOC_CONTAINER_TYPE, SignatureProfile.LT);
    assertNotNull(signature.getTrustedSigningTime());
    assertEquals(signature.getTimeStampCreationTime(), signature.getTrustedSigningTime());
  }

  @Test
  public void bDocLTASignature_TrustedSigningTime_shouldReturnTimeStampCreationTime() throws Exception {
    Signature signature = createSignatureFor(BDOC_CONTAINER_TYPE, SignatureProfile.LTA);
    assertNotNull(signature.getTrustedSigningTime());
    assertEquals(signature.getTimeStampCreationTime(), signature.getTrustedSigningTime());
  }

  @Test
  public void getSignatureSigningCertificateDetails() throws Exception {
    Container container = TestDataBuilder.open("src/test/resources/testFiles/valid-containers/valid-bdoc-tm.bdoc");
    Signature signature = container.getSignatures().get(0);
    X509Cert cert = signature.getSigningCertificate();
    assertEquals("11404176865", cert.getSubjectName(SERIALNUMBER));
    assertEquals("märü-lööz", cert.getSubjectName(GIVENNAME).toLowerCase());
    assertEquals("žõrinüwšky", cert.getSubjectName(SURNAME).toLowerCase());
  }

  @Test
  public void gettingOcspCertificate_whenTslIsNotLoaded() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    TSLCertificateSource certificateSource = new TSLCertificateSourceImpl();
    configuration.setTSL(certificateSource);

    Container container = ContainerBuilder.
        aContainer().
        withConfiguration(configuration).
        fromExistingFile("src/test/resources/testFiles/valid-containers/valid-bdoc-tm.bdoc").
        build();
    Signature signature = container.getSignatures().get(0);

    assertNotNull(signature.getOCSPCertificate());
  }

  @Test
  public void checkCertificateSSCDSupport() {
    // Configuration configuration = new Configuration(Configuration.Mode.TEST);
    Configuration configuration = new Configuration(Configuration.Mode.PROD);
    // configuration.setSslTruststorePath("keystore/keystore.jks");
    TslManager tslManager = new TslManager(configuration);
    TSLCertificateSource certificateSource = tslManager.getTsl();

    configuration.setTSL(certificateSource);

    DSSDocument document = new FileDocument("src/test/resources/testFiles/valid-containers/valid_edoc2_lv-eId_sha256.edoc");
    SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(document);

    SKCommonCertificateVerifier verifier = new SKCommonCertificateVerifier();
    OcspSourceBuilder ocspSourceBuilder = new OcspSourceBuilder();
    OCSPSource ocspSource = ocspSourceBuilder.withConfiguration(configuration).build();
    verifier.setOcspSource(ocspSource);
    verifier.setTrustedCertSource(configuration.getTSL());
    verifier.setDataLoader(new CommonsDataLoader());
    validator.setCertificateVerifier(verifier);
    Reports reports = validator.validateDocument();

    boolean isValid = true;
    for (String signatureId : reports.getSimpleReport().getSignatureIdList()) {
      isValid = isValid && reports.getSimpleReport().isSignatureValid(signatureId);
    }
    assertTrue(isValid);
  }

  @Test
  public void signatureReportForTwoSignature() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.PROD);
    Container container = open("src/test/resources/testFiles/valid-containers/asics_testing_two_signatures.bdoc", configuration);
    ValidationResult result = container.validate();

    assertEquals(Indication.INDETERMINATE, result.getIndication("S0"));
    assertEquals(SubIndication.NO_CERTIFICATE_CHAIN_FOUND, result.getSubIndication("S0"));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification("S0").getLabel());

    assertEquals(Indication.INDETERMINATE, result.getIndication("S1"));
    assertEquals(SubIndication.NO_CERTIFICATE_CHAIN_FOUND, result.getSubIndication("S1"));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification("S1").getLabel());

    assertEquals(Indication.INDETERMINATE, result.getIndication(null));
    assertEquals(SubIndication.NO_CERTIFICATE_CHAIN_FOUND, result.getSubIndication(null));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification(null).getLabel());
  }

  @Test
  public void signatureReportForOneSignature() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    Container container = open("src/test/resources/testFiles/valid-containers/valid-bdoc-tm.bdoc", configuration);
    ValidationResult result = container.validate();

    for ( SimpleReport signatureSimpleReport :  result.getSignatureSimpleReports()) {
      for ( String id: signatureSimpleReport.getSignatureIdList()) {
        //"id-6a5d6671af7a9e0ab9a5e4d49d69800d"
        assertEquals(Indication.TOTAL_PASSED, result.getIndication(id));
        assertEquals(null, result.getSubIndication(id));
        assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification(id).getLabel());
      }
    }

    assertEquals(Indication.TOTAL_PASSED, result.getIndication(null));
    assertEquals(null, result.getSubIndication(null));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification(null).getLabel());
  }

  @Test
  public void signatureReportNoSignature() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    Container container = open("src/test/resources/testFiles/valid-containers/container_without_signatures.bdoc", configuration);
    ValidationResult result = container.validate();

    assertEquals(null, result.getIndication("S0"));
    assertEquals(null, result.getSubIndication("S0"));
    assertEquals(null, result.getSignatureQualification("S0"));

    assertEquals(null, result.getIndication(null));
    assertEquals(null, result.getSubIndication(null));
    assertEquals(null, result.getSignatureQualification(null));
  }

  @Test
  public void signatureReportOnlyOneSignatureValid() throws Exception {
    Configuration configuration = new Configuration(Configuration.Mode.TEST);
    Container container = open("src/test/resources/testFiles/invalid-containers/two_signatures_one_invalid.bdoc", configuration);
    ValidationResult result = container.validate();

    //Signature with id "S1" is invalid
    assertEquals(Indication.INDETERMINATE, result.getIndication("S1"));
    assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, result.getSubIndication("S1"));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification("S1").getLabel());

    //Signature with id "S0" is valid
    assertEquals(Indication.TOTAL_PASSED, result.getIndication(null));
    assertEquals(null, result.getSubIndication(null));
    assertEquals(SignatureQualification.NA.getLabel(), result.getSignatureQualification(null).getLabel());
  }

  private Container open(String path, Configuration configuration){
    Container container = ContainerBuilder.
        aContainer(BDOC_CONTAINER_TYPE).
        fromExistingFile(path).
        withConfiguration(configuration).
        build();
    return container;
  }

  private Signature getSignature(Container.DocumentType documentType) {
    Container container = ContainerBuilder.
        aContainer(documentType.name()).
        withConfiguration(new Configuration(Configuration.Mode.TEST)).
        withDataFile("src/test/resources/testFiles/helper-files/test.txt", "text/plain").
        build();

    Signature signature = SignatureBuilder.
        aSignature(container).
        withSignatureToken(PKCS12_SIGNER).
        invokeSigning();
    container.addSignature(signature);

    return signature;
  }

  private Container createDDoc() {
    return ContainerBuilder.
        aContainer(DDOC_CONTAINER_TYPE).
        build();
  }

  private void verifySignatureWithoutProductionPlaceDoesntThrow(Container container) {
    Signature signature = SignatureBuilder.
        aSignature(container).
        withSignatureToken(PKCS12_SIGNER).
        invokeSigning();
    assertProductionPlaceIsNull(signature);
  }

  private void assertProductionPlaceIsNull(Signature signature) {
    assertEquals("", signature.getCity());
    assertEquals("", signature.getCountryName());
    assertEquals("", signature.getPostalCode());
    assertEquals("", signature.getStateOrProvince());
  }

  private Signature createSignatureFor(String containerType, SignatureProfile signatureProfile) throws IOException {
    Container container = TestDataBuilder.createContainerWithFile(testFolder, containerType);
    Signature signature = SignatureBuilder.
        aSignature(container).
        withSignatureToken(PKCS12_SIGNER).
        withSignatureProfile(signatureProfile).
        invokeSigning();
    return signature;
  }
}
