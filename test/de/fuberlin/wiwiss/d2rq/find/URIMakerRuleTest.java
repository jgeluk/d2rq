package de.fuberlin.wiwiss.d2rq.find;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.vocabulary.RDF;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Expression;
import de.fuberlin.wiwiss.d2rq.algebra.RDFRelation;
import de.fuberlin.wiwiss.d2rq.algebra.RDFRelationImpl;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.RelationImpl;
import de.fuberlin.wiwiss.d2rq.find.URIMakerRule.URIMakerRuleChecker;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.values.Column;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.vocab.FOAF;
import junit.framework.TestCase;

/**
 * :cm1 a d2rq:ClassMap;
 *     d2rq:uriPattern "http://test/person@@employees.ID@@";
 *     d2rq:class foaf:Person;
 *     d2rq:propertyBridge [
 *         d2rq:property foaf:knows;
 *         d2rq:uriPattern "http://test/person@@employees.manager@@";
 *     ];
 *     d2rq:propertyBridge [
 *         d2rq:property foaf:homepage;
 *         d2rq:uriColumn "employees.homepage";
 *     ];
 *     .
 * :cm2 a d2rq:ClassMap;
 *     d2rq:uriColumn "employees.homepage";
 *     d2rq:class foaf:Document;
 * 
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @version $Id: URIMakerRuleTest.java,v 1.1 2006/09/17 00:36:27 cyganiak Exp $
 */
public class URIMakerRuleTest extends TestCase {
	private RDFRelation withURIPatternSubject;
	private RDFRelation withURIPatternSubjectAndObject;
	private RDFRelation withURIColumnSubject;
	private RDFRelation withURIPatternSubjectAndURIColumnObject;
	private URIMakerRuleChecker employeeChecker;
	private URIMakerRuleChecker foobarChecker;

	public void setUp() {
		Relation base = new RelationImpl(null, AliasMap.NO_ALIASES, 
				Collections.EMPTY_MAP, Expression.TRUE, Collections.EMPTY_SET);
		this.withURIPatternSubject = new RDFRelationImpl(base,
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Pattern("http://test/person@@employees.ID@@"), true),
				new FixedNodeMaker(RDF.type.asNode(), false),
				new FixedNodeMaker(FOAF.Person.asNode(), false));
		this.withURIPatternSubjectAndObject = new RDFRelationImpl(base,
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Pattern("http://test/person@@employees.ID@@"), true),
				new FixedNodeMaker(FOAF.knows.asNode(), false),
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Pattern("http://test/person@@employees.manager@@"), true));
		this.withURIColumnSubject = new RDFRelationImpl(base,
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Column(new Attribute(null, "employees", "homepage")), false),
				new FixedNodeMaker(RDF.type.asNode(), false),
				new FixedNodeMaker(FOAF.Document.asNode(), false));
		this.withURIPatternSubjectAndURIColumnObject = new RDFRelationImpl(base,
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Pattern("http://test/person@@employees.ID@@"), true),
				new FixedNodeMaker(FOAF.homepage.asNode(), false),
				new TypedNodeMaker(TypedNodeMaker.URI, 
						new Column(new Attribute(null, "employees", "homepage")), false));
		this.employeeChecker = new URIMakerRule().createRuleChecker(
				Node.createURI("http://test/person1"));
		this.foobarChecker = new URIMakerRule().createRuleChecker(
				Node.createURI("http://test/foobar"));
	}
	
	public void testComparator() {
		URIMakerRule u = new URIMakerRule();
		assertEquals(0, u.compare(this.withURIPatternSubject, this.withURIPatternSubject));
		assertEquals(1, u.compare(this.withURIPatternSubject, this.withURIPatternSubjectAndObject));
		assertEquals(-1, u.compare(this.withURIPatternSubject, this.withURIColumnSubject));
		assertEquals(-1, u.compare(this.withURIPatternSubject, this.withURIPatternSubjectAndURIColumnObject));

		assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubject));
		assertEquals(0, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubjectAndObject));
		assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIColumnSubject));
		assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubjectAndURIColumnObject));

		assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubject));
		assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubjectAndObject));
		assertEquals(0, u.compare(this.withURIColumnSubject, this.withURIColumnSubject));
		assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubjectAndURIColumnObject));

		assertEquals(1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubject));
		assertEquals(1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubjectAndObject));
		assertEquals(-1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIColumnSubject));
		assertEquals(0, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubjectAndURIColumnObject));
	}
	
	public void testSort() {
		Collection unsorted = new ArrayList(Arrays.asList(new RDFRelation[]{
				this.withURIColumnSubject,
				this.withURIPatternSubject, 
				this.withURIPatternSubjectAndObject,
				this.withURIPatternSubjectAndURIColumnObject
		}));
		Collection sorted = new ArrayList(Arrays.asList(new RDFRelation[]{
				this.withURIPatternSubjectAndObject,
				this.withURIPatternSubject, 
				this.withURIPatternSubjectAndURIColumnObject,
				this.withURIColumnSubject
		}));
		assertEquals(sorted, new URIMakerRule().sortRDFRelations(unsorted));
	}
	
	public void testRuleCheckerStartsAccepting() {
		assertTrue(this.employeeChecker.canMatch(this.withURIColumnSubject.nodeMaker(0)));
		assertTrue(this.employeeChecker.canMatch(this.withURIPatternSubject.nodeMaker(0)));
	}
	
	public void testRuleCheckerUnaffectedByNonURIPattern() {
		this.employeeChecker.addPotentialMatch(this.withURIColumnSubject.nodeMaker(0));
		assertTrue(this.employeeChecker.canMatch(this.withURIColumnSubject.nodeMaker(0)));
		assertTrue(this.employeeChecker.canMatch(this.withURIPatternSubject.nodeMaker(0)));
	}
	
	public void testRuleCheckerRejectsAfterMatch() {
		this.employeeChecker.addPotentialMatch(this.withURIPatternSubject.nodeMaker(0));
		assertFalse(this.employeeChecker.canMatch(this.withURIColumnSubject.nodeMaker(0)));
		assertTrue(this.employeeChecker.canMatch(this.withURIPatternSubject.nodeMaker(0)));
	}
	
	public void testRuleCheckerDoesNotRejectAfterNonMatch() {
		this.foobarChecker.addPotentialMatch(this.withURIPatternSubject.nodeMaker(0));
		assertTrue(this.foobarChecker.canMatch(this.withURIColumnSubject.nodeMaker(0)));
		assertTrue(this.foobarChecker.canMatch(this.withURIPatternSubject.nodeMaker(0)));
	}
}