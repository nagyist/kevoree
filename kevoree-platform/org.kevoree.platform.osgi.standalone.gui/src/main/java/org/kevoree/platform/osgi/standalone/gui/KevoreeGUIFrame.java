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
package org.kevoree.platform.osgi.standalone.gui;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class KevoreeGUIFrame extends JFrame {

    private static KevoreeGUIFrame singleton = null;

    public KevoreeGUIFrame() {
        singleton = this;
        this.setBackground(new Color(63, 128, 187));

        URL urlIcon = getClass().getClassLoader().getResource("kevoree-logo-full.png");
        ImageIcon topIIcon = new ImageIcon(urlIcon);
        JLabel topImage = new JLabel(topIIcon);
        this.add(topImage, BorderLayout.NORTH);

        String response = JOptionPane.showInputDialog(this,
                "Kevoree node name ?",
                "Kevoree node runtime",
                JOptionPane.QUESTION_MESSAGE);

        if (response == null) {
            System.exit(0);
        }

        singleton.setVisible(true);

        singleton.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


    }

    public static void showConsole(JPanel p) {
        singleton.add(p);
        singleton.pack();
    }


}
