package pipe.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoableEdit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import pipe.gui.PetriNetTab;
import pipe.historyActions.component.DeletePetriNetObject;
import uk.ac.imperial.pipe.dsl.ANormalArc;
import uk.ac.imperial.pipe.dsl.APetriNet;
import uk.ac.imperial.pipe.dsl.APlace;
import uk.ac.imperial.pipe.dsl.AToken;
import uk.ac.imperial.pipe.dsl.AnImmediateTransition;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentException;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentNotFoundException;
import uk.ac.imperial.pipe.models.petrinet.ArcPoint;
import uk.ac.imperial.pipe.models.petrinet.DiscretePlace;
import uk.ac.imperial.pipe.models.petrinet.DiscreteTransition;
import uk.ac.imperial.pipe.models.petrinet.InboundArc;
import uk.ac.imperial.pipe.models.petrinet.InboundNormalArc;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.models.petrinet.PetriNetComponent;
import uk.ac.imperial.pipe.models.petrinet.Place;
import uk.ac.imperial.pipe.models.petrinet.PlaceStatusNormal;
import uk.ac.imperial.pipe.models.petrinet.Token;
import uk.ac.imperial.pipe.models.petrinet.Transition;
import uk.ac.imperial.pipe.visitor.PlaceBuilder;
import uk.ac.imperial.pipe.visitor.TranslationVisitor;
import uk.ac.imperial.pipe.visitor.component.PetriNetComponentVisitor;

@RunWith(MockitoJUnitRunner.class)
public class PetriNetControllerTest {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private PetriNetController controller;

    private PetriNet net;

    @Mock
    private GUIAnimator mockAnimator;

    @Mock
    private PetriNetTab mocKTab;

    @Mock
    UndoableEditListener undoListener;

	private CopyPasteManager copyPasteManager;

	private ZoomController zoomController;

    @Before
    public void setUp() {
        net = new PetriNet();
        copyPasteManager = mock(CopyPasteManager.class);
        zoomController = mock(ZoomController.class);

        controller = new PetriNetController(net, undoListener, mockAnimator, copyPasteManager, zoomController, mocKTab);
    }

    @Test
    public void containsSelected() {
        PetriNetComponent dummyComponent = new DummyPetriNetComponent();
        controller.select(dummyComponent);
        assertTrue(controller.isSelected(dummyComponent));
    }

    @Test
    public void doesNotContainDeselected() {
        PetriNetComponent dummyComponent = new DummyPetriNetComponent();
        controller.select(dummyComponent);
        controller.deselect(dummyComponent);
        assertFalse(controller.isSelected(dummyComponent));
    }

    @Test
    public void deselectAllEmptiesSelected() {
        PetriNetComponent dummyComponent = new DummyPetriNetComponent();
        controller.select(dummyComponent);
        controller.deselectAll();
        assertFalse(controller.isSelected(dummyComponent));
    }

    @Test
    public void deletesSelectedRemovesFromNet() throws PetriNetComponentException {
        Place place = new DiscretePlace("", "");
        net.addPlace(place);

        controller.select(place);
        controller.deleteSelection();
        assertFalse(net.getPlaces().contains(place));
    }

    @Test
    public void deletingSelectionReturnsListOfAbstractUndoEdits() throws PetriNetComponentException {
        Place place = new DiscretePlace("", "");
        net.addPlace(place);

        controller.select(place);
        List<UndoableEdit> edits = controller.deleteSelection();
        DeletePetriNetObject deleteAction = new DeletePetriNetObject(place, net);
        assertEquals(1, edits.size());
        assertTrue(edits.contains(deleteAction));
    }

    @Test
    public void deleteComponentRemovesFromPetriNet() throws PetriNetComponentException {
        Place place = new DiscretePlace("", "");
        net.addPlace(place);

        controller.delete(place);

        assertFalse("Petrinet contains place after deletion", net.getPlaces().contains(place));
    }

    @Test
    public void deletingComponentAddsToHistoryManager() throws PetriNetComponentException {
        Place place = new DiscretePlace("", "");
        net.addPlace(place);

        UndoableEdit edit = controller.delete(place);

        DeletePetriNetObject deleteAction = new DeletePetriNetObject(place, net);
        assertEquals(edit, deleteAction);
    }

    @Test
    public void deletesSelectedNotifiesObserver() throws PetriNetComponentException {
        Place place = new DiscretePlace("", "");
        net.addPlace(place);

        PropertyChangeListener mockListener = mock(PropertyChangeListener.class);
        net.addPropertyChangeListener(mockListener);

        controller.select(place);
        controller.deleteSelection();
        verify(mockListener, atLeastOnce()).propertyChange(any(PropertyChangeEvent.class));
    }

    @Test
    public void selectsItemLocatedWithinSelectionArea() {
        Place place = new DiscretePlace("P0") {
        	@Override
        	public int getWidth() { return 5; }
        	@Override
        	public int getHeight() { return 20; }
        };
        place.setX(5);
        place.setY(10);
        net.addPlace(place);

        Rectangle selectionRectangle = new Rectangle(5, 10, 40, 40);
        controller.select(selectionRectangle);
        assertTrue(controller.isSelected(place));
    }

    /**
     * Even if top left x, y is out of the selection area if the height and width are in then select item
     */
    @Test
    public void selectsItemWithWidthAndHeightWithinSelectionArea() {
        Place place = new DiscretePlace("P0") {
        	@Override
        	public int getWidth() { return 10; }
        	@Override
        	public int getHeight() { return 10; }
        };
        place.setX(0);
        place.setY(0);
        net.addPlace(place);

        Rectangle selectionRectangle = new Rectangle(5, 5, 40, 40);
        controller.select(selectionRectangle);

        assertTrue(controller.isSelected(place));
    }

    @Test
    public void selectsArcIfIntersects() throws Exception {
		InboundNormalArc arc = buildArcFrom0to10();
        Rectangle selectionRectangle = new Rectangle(0, 0, 2, 2);
        controller.select(selectionRectangle);
        assertTrue(controller.isSelected(arc));
    }
    @Test
    public void doesNotSelectArcIfDoesntIntersect() throws Exception {
		InboundNormalArc arc = buildArcFrom0to10();
        Rectangle selectionRectangle = new Rectangle(30, 30, 40, 40);
        controller.select(selectionRectangle);
        assertFalse(controller.isSelected(arc));
    }
    public InboundNormalArc buildArcFrom0to10() throws PetriNetComponentException, PetriNetComponentNotFoundException {
    	net = APetriNet.with(AToken.called("Default").withColor(Color.BLACK)).and(APlace.withId("P0")).
    			andFinally(AnImmediateTransition.withId("T0"));
    	controller = new PetriNetController(net, undoListener, mockAnimator, copyPasteManager, zoomController, mocKTab);
    	InboundNormalArc arc = new InboundNormalArc(net.getComponent("P0", Place.class), 
    			net.getComponent("T0", Transition.class), new HashMap<String, String>()){
    		@Override
    		public List<ArcPoint> getArcPoints() {
    			return Arrays.asList(new ArcPoint(new Point2D.Double(0, 0), false), 
    					new ArcPoint(new Point2D.Double(10, 10), false));
    		}
    	};
    	
    	net.addArc(arc);
    	return arc;
    }
    

    @Test
    public void translatesSelectedItemsCorrectly() throws PetriNetComponentException {
    	int x_y_value = 40;
    	Place place = new DiscretePlace("P0");
    	Transition transition = new DiscreteTransition("T0");
        place.setX(x_y_value);
        place.setY(x_y_value);
        transition.setX(x_y_value);
        transition.setY(x_y_value);
        net.addPlace(place);
        net.addTransition(transition);

        controller.select(place);
        controller.select(transition);
        int translate_value = 50;
        controller.translateSelected(new Point(translate_value, translate_value));

        int expected_value = x_y_value + translate_value;
        assertEquals(expected_value, place.getX());
        assertEquals(expected_value, place.getY());
        assertEquals(expected_value, transition.getX());
        assertEquals(expected_value, transition.getY());
    }

    @Test
    public void doesNotTranslateNonDraggableItems() throws PetriNetComponentException {
        PetriNetComponent petriNetComponent = mock(PetriNetComponent.class);
        when(petriNetComponent.isDraggable()).thenReturn(false);
        controller.select(petriNetComponent);
        controller.translateSelected(new Point(23, 58));
        verify(petriNetComponent, never()).accept(any(TranslationVisitor.class));
    }

    @Test
    public void updateNameIfChanged() throws PetriNetComponentNotFoundException {
        Token token = mock(Token.class);
        String id = "id";
        when(token.getId()).thenReturn(id);

        boolean enabled = true;

        Color color = Color.RED;
        when(token.getColor()).thenReturn(color);
        net.addToken(token);

        controller.updateToken(id, "new", color);
        verify(token).setId("new");
    }

    @Test
    public void doesNotUpdateNameIfNotChanged() throws PetriNetComponentNotFoundException {
        Token token = mock(Token.class);
        String id = "id";
        when(token.getId()).thenReturn(id);

        boolean enabled = true;

        Color color = new Color(255, 0, 0);
        when(token.getColor()).thenReturn(color);
        net.addToken(token);

        controller.updateToken(id, id, color);
        verify(token, never()).setId(anyString());
    }

    @Test
    public void updateTokenColorIfChanged() throws PetriNetComponentNotFoundException {
        Token token = mock(Token.class);
        String id = "id";
        when(token.getId()).thenReturn(id);

        boolean enabled = true;

        Color color = new Color(255, 0, 0);
        when(token.getColor()).thenReturn(color);
        net.addToken(token);

        Color newColor = new Color(0, 0, 0);
        controller.updateToken(id, id, newColor);
        verify(token).setColor(newColor);
    }

    @Test
    public void doesNotUpdateTokenColorIfNotChanged() throws PetriNetComponentNotFoundException {
        Token token = mock(Token.class);
        String id = "id";
        when(token.getId()).thenReturn(id);

        boolean enabled = true;

        Color color = new Color(255, 0, 0);
        when(token.getColor()).thenReturn(color);
        net.addToken(token);

        controller.updateToken(id, id, color);
        verify(token, never()).setColor(any(Color.class));
    }

    @Test
    public void createNewToken() {
        String name = "testToken";
        boolean enabled = true;
        Color color = new Color(160, 92, 240);

        controller.createNewToken(name, color);
        Collection<Token> tokens = net.getTokens();
        assertEquals(1, tokens.size());
        Token token = tokens.iterator().next();
        assertEquals(name, token.getId());
        assertEquals(color, token.getColor());
    }

    private class DummyPetriNetComponent implements PetriNetComponent {
        @Override
        public boolean isSelectable() {
            return false;
        }

        @Override
        public boolean isDraggable() {
            return false;
        }

        @Override
        public void accept(PetriNetComponentVisitor visitor) {

        }

        @Override
        public String getId() {
            return "";
        }

        @Override
        public void setId(String id) {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {

        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {

        }

		@Override
		public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		}

    }
}
