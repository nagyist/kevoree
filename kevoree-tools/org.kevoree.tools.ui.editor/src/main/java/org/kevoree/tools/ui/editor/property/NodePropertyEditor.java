/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* $Id: NodePropertyEditor.java 13934 2010-12-19 21:56:29Z francoisfouquet $ 
 * License    : EPL 								
 * Copyright  : IRISA / INRIA / Universite de Rennes 1 */
package org.kevoree.tools.ui.editor.property;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import com.explodingpixels.macwidgets.plaf.HudLabelUI;
import com.explodingpixels.macwidgets.plaf.HudTextFieldUI;
import org.kevoree.ContainerNode;
import org.kevoree.Instance;
import org.kevoree.tools.ui.editor.KevoreeUIKernel;
import org.kevoree.tools.ui.editor.command.*;
import org.kevoree.tools.ui.editor.widget.JCommandButton;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author ffouquet
 */
public class NodePropertyEditor extends InstancePropertyEditor {

    public NodePropertyEditor(final Instance elem, KevoreeUIKernel _kernel) {

        super(elem, _kernel);

        final ContainerNode node = (ContainerNode) elem;


        JPanel pnameLayout = new JPanel(new SpringLayout());
        pnameLayout.setBorder(null);
        pnameLayout.setOpaque(false);
        JLabel physicalNodeNameLabel = new JLabel("PhysicalNode", JLabel.TRAILING);
        physicalNodeNameLabel.setUI(new HudLabelUI());
        pnameLayout.add(physicalNodeNameLabel);
        JTextField physicalNodeNameField = new JTextField(15);
        physicalNodeNameField.setUI(new HudTextFieldUI());
        physicalNodeNameLabel.setLabelFor(physicalNodeNameField);
        pnameLayout.add(physicalNodeNameField);

        SpringUtilities.makeCompactGrid(pnameLayout,
                1, 2, //rows, cols
                6, 6,        //initX, initY
                6, 6);

        this.addCenter(pnameLayout);

        final UpdatePhysicalNode commandUpdate = new UpdatePhysicalNode();
        commandUpdate.setKernel(kernel);
        commandUpdate.setTargetCNode(node);

        physicalNodeNameField.setText(commandUpdate.getCurrentPhysName());

        physicalNodeNameField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    commandUpdate.execute(e.getDocument().getText(0, e.getDocument().getLength()));
                } catch (BadLocationException ex) {
                    Logger.getLogger(NamedElementPropertyEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    commandUpdate.execute(e.getDocument().getText(0, e.getDocument().getLength()));
                } catch (BadLocationException ex) {
                    Logger.getLogger(NamedElementPropertyEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    commandUpdate.execute(e.getDocument().getText(0, e.getDocument().getLength()));
                } catch (BadLocationException ex) {
                    Logger.getLogger(NamedElementPropertyEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });


        //textField.setText(namedElem.getName());


        /*
     JTable table = new JTable(new InstanceTableModel(node));
     JScrollPane scrollPane = new JScrollPane(table);
     table.setFillsViewportHeight(true);

     scrollPane.setPreferredSize(new Dimension(300, 150));

     this.addCenter(scrollPane);
        */

        this.addCenter(new NetworkPropertyEditor(node));

/*
        JCommandButton btPush = new JCommandButton("PushModelJgroup");
        SynchPlatformCommand send = new SynchPlatformCommand();
        send.setKernel(_kernel);
        send.setDestNodeName(node.getName());
        btPush.setCommand(send);
        this.addCenter(btPush);*/

/*
        JCommandButton btPushNode = new JCommandButton("PushModelJMDNS");
        SynchNodeCommand sendNode = new SynchNodeCommand();
        sendNode.setKernel(_kernel);
        sendNode.setDestNodeName(node.getName());
        btPushNode.setCommand(sendNode);
        this.addCenter(btPushNode);
*/
        final SynchNodeTypeCommand sendNodeType = new SynchNodeTypeCommand();
        JCommandButton btPushNodeType = new JCommandButton("Push"){
            @Override
            public void doBeforeExecution() {
                sendNodeType.setDestNodeName(elem.getName());
            }
        };

        sendNodeType.setKernel(_kernel);
        sendNodeType.setDestNodeName(node.getName());
        btPushNodeType.setCommand(sendNodeType);
        this.addCenter(btPushNodeType);


        //   JTextField ip = new JTextField("ip:port");
        /*
        JCommandButton btPushNodeIP = new JCommandButton("Push");
        SynchNodeIPCommand sendNodeIP = new SynchNodeIPCommand();


        sendNodeIP.setKernel(_kernel);
        sendNodeIP.setDestNodeName(node.getName());
        btPushNodeIP.setCommand(sendNodeIP);
        //this.addCenter(ip);
        this.addCenter(btPushNodeIP);
              */

    }
}
