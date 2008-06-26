/*******************************************************************************
 * Copyright (c) 2008 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.xtext.ui.editor;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.DefaultTextHover;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.formatter.MultiPassContentFormatter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.texteditor.HippieProposalProcessor;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.parsetree.AbstractNode;
import org.eclipse.xtext.parsetree.CompositeNode;
import org.eclipse.xtext.parsetree.LeafNode;
import org.eclipse.xtext.service.ILanguageDescriptor;
import org.eclipse.xtext.service.ServiceRegistry;
import org.eclipse.xtext.ui.editor.codecompletion.XtextContentAssistProcessor;
import org.eclipse.xtext.ui.editor.formatting.XtextFormattingStrategy;
import org.eclipse.xtext.ui.editor.model.IEditorModelProvider;
import org.eclipse.xtext.ui.editor.model.XtextEditorModelReconcileStrategy;
import org.eclipse.xtext.ui.editor.utils.EditorModelUtil;
import org.eclipse.xtext.ui.service.IFormatterService;
import org.eclipse.xtext.ui.service.ILabelProvider;
import org.eclipse.xtext.ui.service.IProposalsProvider;

/**
 * 
 * 
 * @author Dennis H�bner - Initial contribution and API
 * 
 */
public class XtextSourceViewerConfiguration extends TextSourceViewerConfiguration {
	final IEditorModelProvider editorModelProvider;
	private final ILanguageDescriptor languageDescriptor;

	/**
	 * @param languageDescriptor
	 * @param preferenceStore
	 * @param editor
	 */
	public XtextSourceViewerConfiguration(ILanguageDescriptor languageDescriptor, IPreferenceStore preferenceStore,
			IEditorModelProvider editor) {
		super(preferenceStore);
		this.languageDescriptor = languageDescriptor;
		this.editorModelProvider = editor;
	}

	@Override
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		return new MonoReconciler(new XtextEditorModelReconcileStrategy(editorModelProvider), false);
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		IProposalsProvider proposalsProvider = ServiceRegistry.getService(languageDescriptor, IProposalsProvider.class);
		IContentAssistProcessor processor;
		if (proposalsProvider != null) {
			processor = new XtextContentAssistProcessor(editorModelProvider, proposalsProvider);
		}
		else {
			processor = new HippieProposalProcessor();
		}
		ContentAssistant ca = new ContentAssistant();
		ca.enableColoredLabels(true);
		ca.setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);
		ca.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
		ca.setProposalPopupOrientation(IContentAssistant.PROPOSAL_STACKED);
		ca.setInformationControlCreator(new AbstractReusableInformationControlCreator() {
			@Override
			protected IInformationControl doCreateInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, false);
			}
		});
		return ca;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.eclipse.jface.text.source.SourceViewerConfiguration#
	 * getPresentationReconciler(org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = (PresentationReconciler) super.getPresentationReconciler(sourceViewer);
		DefaultDamagerRepairer defDR = new DefaultDamagerRepairer(new XtextTokenScanner(languageDescriptor));
		reconciler.setRepairer(defDR, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setDamager(defDR, IDocument.DEFAULT_CONTENT_TYPE);
		return reconciler;
	}

	/*
	 * Just a little PrototypeFormattingStrategy
	 * 
	 * @see
	 * org.eclipse.jface.text.source.SourceViewerConfiguration#getContentFormatter
	 * (org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
		IFormatterService service = ServiceRegistry.getService(languageDescriptor, IFormatterService.class);
		if (service != null) {
			MultiPassContentFormatter formatter = new MultiPassContentFormatter(
					getConfiguredDocumentPartitioning(sourceViewer), IDocument.DEFAULT_CONTENT_TYPE);
			formatter.setMasterStrategy(new XtextFormattingStrategy(this.editorModelProvider, service));
			return formatter;
		}
		else
			return null;
	}

	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
		ITextHover textHover = new XtextTextHover(sourceViewer);
		return textHover;
	}

	class XtextTextHover extends DefaultTextHover {
		public XtextTextHover(ISourceViewer sourceViewer) {
			super(sourceViewer);
		}

		protected boolean isIncluded(Annotation annotation) {
			return isShownInText(annotation);
		}

		/*
		 * @see
		 * org.eclipse.jface.text.ITextHoverExtension#getHoverControlCreator()
		 */
		public IInformationControlCreator getHoverControlCreator() {
			return new IInformationControlCreator() {
				public IInformationControl createInformationControl(Shell parent) {
					return new DefaultInformationControl(parent, EditorsUI.getTooltipAffordanceString());
				}
			};
		}

		@Override
		public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
			return new Region(offset, 0);
		}

		@Override
		public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
			ILabelProvider lp = ServiceRegistry.getService(languageDescriptor, ILabelProvider.class);
			String textHover = super.getHoverInfo(textViewer, hoverRegion);
			if (textHover == null) {
				textHover = "";
			}
			else {
				textHover += "\n";
			}
			StringBuilder sb = new StringBuilder(textHover);
			AbstractNode an = EditorModelUtil.findLeafNodeAtOffset(editorModelProvider.getModel()
					.getParseTreeRootNode(), hoverRegion.getOffset());
			if (an instanceof LeafNode) {
				LeafNode leaf = (LeafNode) an;
				if (!leaf.isHidden()) {
					handleFeatured(leaf, sb);
					sb.append(leaf.getText());
					handleCoordinaten(leaf, sb);
					handleParent(leaf.getParent(), sb);
				}
			}
			return sb.toString();
		}

		private void handleCoordinaten(AbstractNode abstractNode, StringBuilder sb) {
			sb.append(" [" + abstractNode.offset() + "," + abstractNode.length() + "]");
		}

		private void handleParent(CompositeNode parent, StringBuilder sb) {
			sb.append("\n\t" + "Parent: ");
			grammarEleToString(parent.getGrammarElement(), sb);
			handleCoordinaten(parent, sb);
		}

		private void grammarEleToString(EObject grammarElement, StringBuilder sb) {
			if (grammarElement instanceof RuleCall)
				sb.append(((RuleCall) grammarElement).getName());
			if (grammarElement instanceof ParserRule)
				sb.append(((ParserRule) grammarElement).getName());

		}

		private void handleFeatured(LeafNode leaf, StringBuilder sb) {
			if (leaf.getFeature() != null) {
				sb.append(((RuleCall) leaf.getGrammarElement()).getName());
				sb.append(" - ");
				grammarEleToString(leaf.getParent().getGrammarElement(), sb);
				sb.append(".");
				sb.append(leaf.getFeature());

				sb.append("\n");
			}
		}
	}
}
