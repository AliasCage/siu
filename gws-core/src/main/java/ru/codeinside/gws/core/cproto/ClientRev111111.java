/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2013, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.gws.core.cproto;

import ru.codeinside.gws.api.CryptoProvider;
import ru.codeinside.gws.api.Revision;
import ru.codeinside.gws.api.ServiceDefinitionParser;
import ru.codeinside.gws.api.XmlNormalizer;
import ru.codeinside.gws.api.XmlSignatureInjector;
import ru.codeinside.gws.core.Xml;

public class ClientRev111111 extends ClientProtocolImpl {

  public ClientRev111111(ServiceDefinitionParser definitionParser, CryptoProvider cryptoProvider,
                         XmlNormalizer xmlNormalizer, XmlSignatureInjector injector) {
    super(Revision.rev111111, Xml.REV111111, "schema/smev-r111111-v1.0.xsd", definitionParser, cryptoProvider,
            xmlNormalizer, injector);
  }
}
